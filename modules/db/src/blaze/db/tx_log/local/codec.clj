(ns blaze.db.tx-log.local.codec
  (:require
    [blaze.byte-string :as bs]
    [blaze.db.impl.byte-buffer :as bb]
    [blaze.db.impl.codec :as codec]
    [jsonista.core :as j]
    [taoensso.timbre :as log])
  (:import
    [com.fasterxml.jackson.dataformat.cbor CBORFactory]
    [java.time Instant]))


(def ^:private cbor-object-mapper
  (j/object-mapper
    {:factory (CBORFactory.)
     :decode-key-fn true
     :modules [bs/object-mapper-module]}))


(defn encode-key [t]
  (-> (bb/allocate Long/BYTES) (bb/put-long! t) bb/array))


(defn encode-tx-data [instant tx-cmds]
  (j/write-value-as-bytes
    {:instant (inst-ms instant)
     :tx-cmds tx-cmds}
    cbor-object-mapper))


(defn- parse-cbor-error-msg [t e]
  (format "Skip transaction with point in time of %d because there was an error while parsing tx-data: %s"
          t (ex-message e)))


(defn- parse [value t]
  (try
    (j/read-value value cbor-object-mapper)
    (catch Exception e
      (log/warn (parse-cbor-error-msg t e)))))


(defn- decode-hash [tx-cmd]
  (update tx-cmd :hash bs/from-byte-array))


(defn- decode-instant [x]
  (when (int? x)
    (Instant/ofEpochMilli x)))


(defn decode-tx-data
  ([]
   [(bb/allocate-direct codec/t-size)
    (bb/allocate-direct 1024)])
  ([kb vb]
   (let [t (bb/get-long! kb)
         value (byte-array (bb/remaining vb))]
     (bb/copy-into-byte-array! vb value 0 (bb/remaining vb))
     (-> (parse value t)
         (update :tx-cmds #(mapv decode-hash %))
         (update :instant decode-instant)
         (assoc :t t)))))
