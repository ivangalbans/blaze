(ns blaze.elm.equiv-relationships
  "Finds relationships (with and without) in queries, that have an equals
  expression resulting in equiv semi-joins and semi-differences."
  (:require
    [blaze.elm.spec]
    [cuerdas.core :as c-str]))


(defmulti find-equiv-rels
  {:arglists '([expression])}
  (fn [{:keys [type]}]
    (assert type)
    (keyword "elm.equiv-relationships.type" (c-str/kebab type))))


(defn- update-expression-defs [expression-defs]
  (mapv #(update % :expression find-equiv-rels) expression-defs))


(defn find-equiv-rels-library [library]
  (update-in library [:statements :def] update-expression-defs))


(defmethod find-equiv-rels :default
  [expression]
  expression)


(defmethod find-equiv-rels :elm.equiv-relationships.type/unary-expression
  [expression]
  (update expression :operand find-equiv-rels))


(defmethod find-equiv-rels :elm.equiv-relationships.type/multiary-expression
  [expression]
  (update expression :operand #(mapv find-equiv-rels %)))


(defn split-by-first-equal-expression
  "Searches for the first Equal expression which is a mandatory condition
  (combined with And) and splits the expression tree into that and the rest.

  Returns a map with :equal-expr and :rest-expr. Retains the whole expression in
  :rest-expr if no equal expression is found."
  [{:keys [type] :as expression}]
  (case type
    "Equal"
    {:equal-expr expression}

    "And"
    (let [[operand-1 operand-2] (:operand expression)
          {:keys [equal-expr rest-expr]} (split-by-first-equal-expression operand-1)]
      (if equal-expr
        {:equal-expr equal-expr
         :rest-expr
         (if rest-expr
           {:type "And" :operand [rest-expr operand-2]}
           operand-2)}
        (let [{:keys [equal-expr rest-expr]} (split-by-first-equal-expression operand-2)]
          (if equal-expr
            {:equal-expr equal-expr
             :rest-expr
             (if rest-expr
               {:type "And" :operand [operand-1 rest-expr]}
               operand-1)}
            {:rest-expr expression}))))

    {:rest-expr expression}))


(defn- find-equiv-relationship
  [{such-that :suchThat :as relationship}]
  (let [{:keys [equal-expr rest-expr]} (split-by-first-equal-expression such-that)]
    (if equal-expr
      (cond->
        (-> relationship
            (update :type str "Equiv")
            (assoc :equivOperand (:operand equal-expr)))
        (some? rest-expr)
        (assoc :suchThat rest-expr)
        (nil? rest-expr)
        (dissoc :suchThat))
      relationship)))


(defmethod find-equiv-rels :elm.equiv-relationships.type/query
  [{relationships :relationship :as expression}]
  (assoc expression :relationship (mapv find-equiv-relationship relationships)))



;; 20. List Operators

;; 20.4. Distinct
(derive :elm.equiv-relationships.type/distinct :elm.equiv-relationships.type/unary-expression)


;; 20.8. Exists
(derive :elm.equiv-relationships.type/exists :elm.equiv-relationships.type/unary-expression)


;; 20.10. First
(defmethod find-equiv-rels :elm.equiv-relationships.type/first
  [expression]
  (update expression :source find-equiv-rels))


;; 20.11. Flatten
(derive :elm.equiv-relationships.type/flatten :elm.equiv-relationships.type/unary-expression)


;; 20.25. SingletonFrom
(derive :elm.equiv-relationships.type/singleton-from :elm.equiv-relationships.type/unary-expression)
