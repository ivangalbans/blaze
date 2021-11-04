(ns blaze.db.api-stub
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-spec]
    [blaze.db.kv :as kv]
    [blaze.db.kv.mem]
    [blaze.db.kv.mem-spec]
    [blaze.db.node]
    [blaze.db.resource-handle-cache]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.kv :as rs-kv]
    [blaze.db.search-param-registry]
    [blaze.db.tx-cache]
    [blaze.db.tx-log :as tx-log]
    [blaze.db.tx-log-spec]
    [blaze.db.tx-log.local]
    [blaze.test-util :refer [with-system]]
    [integrant.core :as ig]
    [java-time :as time]))


(def mem-node-system
  {:blaze.db/node
   {:tx-log (ig/ref :blaze.db/tx-log)
    :resource-handle-cache (ig/ref :blaze.db/resource-handle-cache)
    :tx-cache (ig/ref :blaze.db/tx-cache)
    :indexer-executor (ig/ref :blaze.db.node/indexer-executor)
    :resource-store (ig/ref :blaze.db/resource-store)
    :kv-store (ig/ref :blaze.db/index-kv-store)
    :search-param-registry (ig/ref :blaze.db/search-param-registry)
    :poll-timeout (time/millis 10)}

   ::tx-log/local
   {:kv-store (ig/ref :blaze.db/transaction-kv-store)
    :clock (ig/ref :blaze.test/clock)}
   [::kv/mem :blaze.db/transaction-kv-store]
   {:column-families {}}
   :blaze.test/clock {}

   :blaze.db/resource-handle-cache {}

   :blaze.db/tx-cache
   {:kv-store (ig/ref :blaze.db/index-kv-store)}

   :blaze.db.node/indexer-executor {}

   [::kv/mem :blaze.db/index-kv-store]
   {:column-families
    {:search-param-value-index nil
     :resource-value-index nil
     :compartment-search-param-value-index nil
     :compartment-resource-type-index nil
     :active-search-params nil
     :tx-success-index {:reverse-comparator? true}
     :tx-error-index nil
     :t-by-instant-index {:reverse-comparator? true}
     :resource-as-of-index nil
     :type-as-of-index nil
     :system-as-of-index nil
     :type-stats-index nil
     :system-stats-index nil}}

   ::rs/kv
   {:kv-store (ig/ref :blaze.db/resource-kv-store)
    :executor (ig/ref ::rs-kv/executor)}
   [::kv/mem :blaze.db/resource-kv-store]
   {:column-families {}}
   ::rs-kv/executor {}

   :blaze.db/search-param-registry {}})


(defmacro with-system-data [[binding-form system] txs & body]
  `(with-system [system# ~system]
     (run! #(deref (d/transact (:blaze.db/node system#) %)) ~txs)
     (let [~binding-form system#] ~@body)))
