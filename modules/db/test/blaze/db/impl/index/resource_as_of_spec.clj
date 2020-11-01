(ns blaze.db.impl.index.resource-as-of-spec
  (:require
    [blaze.db.impl.batch-db.spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.resource-as-of :as resource-as-of]
    [blaze.db.impl.index.resource-handle-spec]
    [blaze.db.impl.iterators-spec]
    [blaze.db.kv-spec]
    [blaze.db.spec]
    [blaze.fhir.spec]
    [clojure.spec.alpha :as s]))


(s/fdef resource-as-of/type-list
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :start-id (s/nilable :blaze.db/id-bytes))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef resource-as-of/system-list
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :start-tid (s/nilable :blaze.db/tid)
               :start-id (s/nilable :blaze.db/id-bytes))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef resource-as-of/instance-history
  :args (s/cat :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :id :blaze.resource/id
               :start-t :blaze.db/t
               :end-t :blaze.db/t)
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef resource-as-of/hash-state-t
  :args (s/cat :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :t :blaze.db/t)
  :ret (s/nilable (s/tuple :blaze.resource/hash :blaze.db/state :blaze.db/t)))


(s/fdef resource-as-of/resource-handle
  :args (s/cat :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes)
  :ret (s/nilable :blaze.db/resource-handle))


(s/fdef resource-as-of/num-of-instance-changes
  :args (s/cat :raoi :blaze.db/kv-iterator
               :tid :blaze.db/tid
               :id :blaze.db/id-bytes
               :start-t :blaze.db/t
               :end-t :blaze.db/t)
  :ret nat-int?)
