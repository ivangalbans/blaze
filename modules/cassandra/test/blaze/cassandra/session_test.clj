(ns blaze.cassandra.session-test
  (:require
    [blaze.cassandra.session :as session]
    [blaze.cassandra.session-spec]
    [clojure.core.protocols :as p]
    [clojure.datafy :as datafy]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest]]
    [juxt.iota :refer [given]])
  (:import
    [com.datastax.oss.driver.api.core CqlSessionBuilder CqlIdentifier]
    [com.datastax.oss.driver.api.core.session SessionBuilder]
    [com.datastax.oss.driver.api.core.metadata EndPoint]))


(st/instrument)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn- get-field
  "Access to private or protected field where `field-name` is a symbol or
   keyword."
  [klass field-name obj]
  (-> klass (.getDeclaredField (name field-name))
      (doto (.setAccessible true))
      (.get obj)))


(extend-protocol p/Datafiable
  CqlSessionBuilder
  (datafy [builder]
    {:contactPoints
     (->> (get-field SessionBuilder :programmaticContactPoints builder)
          (mapv datafy/datafy))
     :keyspace (datafy/datafy (get-field SessionBuilder :keyspace builder))})

  CqlIdentifier
  (datafy [identifier]
    (str identifier))

  EndPoint
  (datafy [end-point]
    (str end-point)))


(deftest session-test
  (given (datafy/datafy (session/session-builder {}))
    :keyspace := "blaze"
    :contactPoints := ["localhost/127.0.0.1:9042"]))