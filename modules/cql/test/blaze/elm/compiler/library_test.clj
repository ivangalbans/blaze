(ns blaze.elm.compiler.library-test
  (:require
    [blaze.cql-translator :as t]
    [blaze.db.api-stub :refer [mem-node-system]]
    [blaze.elm.compiler.library :as library]
    [blaze.elm.compiler.library-spec]
    [blaze.fhir.spec.type.system :as system]
    [blaze.test-util :refer [with-system]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest compile-library-test
  (testing "empty library"
    (let [library (t/translate "library Test")]
      (with-system [{:blaze.db/keys [node]} mem-node-system]
        (given (library/compile-library node library {})
          :compiled-expression-defs := {}))))

  (testing "one static expression"
    (let [library (t/translate "library Test define Foo: true")]
      (with-system [{:blaze.db/keys [node]} mem-node-system]
        (given (library/compile-library node library {})
          :compiled-expression-defs := {"Foo" true}))))

  (testing "one static expression"
    (let [library (t/translate "library Test
    using FHIR version '4.0.0'
    context Patient
    define Gender: Patient.gender")]
      (with-system [{:blaze.db/keys [node]} mem-node-system]
        (given (library/compile-library node library {})
          [:compiled-expression-defs "Gender" :key] := :gender
          [:compiled-expression-defs "Gender" :source :name] := "Patient"))))

  (testing "with compile-time error"
    (let [library (t/translate "library Test
    define Error: singleton from {1, 2}")]
      (with-system [{:blaze.db/keys [node]} mem-node-system]
        (given (library/compile-library node library {})
          ::anom/category := ::anom/conflict
          ::anom/message := "More than one element in `SingletonFrom` expression."))))

  (testing "with parameter default"
    (let [library (t/translate "library Test
    parameter \"Measurement Period\" Interval<Date> default Interval[@2020-01-01, @2020-12-31]")]
      (with-system [{:blaze.db/keys [node]} mem-node-system]
        (given (library/compile-library node library {})
          [:parameter-default-values "Measurement Period" :start] := (system/date 2020 1 1)
          [:parameter-default-values "Measurement Period" :end] := (system/date 2020 12 31)))))

  (testing "with invalid parameter default"
    (let [library (t/translate "library Test
    parameter \"Measurement Start\" Integer default singleton from {1, 2}")]
      (with-system [{:blaze.db/keys [node]} mem-node-system]
        (given (library/compile-library node library {})
          ::anom/category := ::anom/conflict
          ::anom/message "More than one element in `SingletonFrom` expression.")))))
