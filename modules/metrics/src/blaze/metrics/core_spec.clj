(ns blaze.metrics.core-spec
  (:require
    [blaze.metrics.core :as metrics]
    [blaze.metrics.spec]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))


(s/fdef metrics/collect
  :args (s/cat :collector :blaze.metrics/collector)
  :ret (s/coll-of :blaze.metrics/metric))


(defn counter-name? [x]
  (and (string? x) (str/ends-with? x "_total")))


(s/fdef metrics/counter-metric
  :args (s/cat :name counter-name? :help string? :label-names (s/coll-of string?)
               :samples (s/coll-of :blaze.metrics/sample)))


(s/fdef metrics/gauge-metric
  :args (s/cat :name string? :help string? :label-names (s/coll-of string?)
               :samples (s/coll-of :blaze.metrics/sample)))


(s/fdef metrics/register!
  :args (s/cat :registry :blaze.metrics/registry
               :collector :blaze.metrics/collector))
