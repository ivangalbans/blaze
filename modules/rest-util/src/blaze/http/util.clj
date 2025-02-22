(ns blaze.http.util
  (:require
    [clojure.core.protocols :refer [Datafiable]]
    [clojure.datafy :refer [datafy]]
    [clojure.string :as str])
  (:import
    [org.apache.hc.core5.http HeaderElement NameValuePair]
    [org.apache.hc.core5.http.message BasicHeaderValueParser ParserCursor]))


(set! *warn-on-reflection* true)


(extend-protocol Datafiable
  HeaderElement
  (datafy [element]
    (let [params (mapv datafy (.getParameters element))]
      (cond->
        {:name (str/lower-case (.getName element))
         :value (.getValue element)}
        (seq params)
        (assoc :params params))))
  NameValuePair
  (datafy [pair]
    {:name (str/lower-case (.getName pair))
     :value (.getValue pair)}))


(defn parse-header-value
  "Parses the header value string `s` into elements which have a :name, a :value
  and possible :params that itself have :name and :value.

  The element name is converted to lower-case."
  [s]
  (when s
    (let [cursor (ParserCursor. 0 (count s))]
      (->> (.parseElements BasicHeaderValueParser/INSTANCE s cursor)
           (mapv datafy)))))
