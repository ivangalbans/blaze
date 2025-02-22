(ns blaze.async.comp
  "This namespace provides functions to work with CompletableFutures.

  https://www.baeldung.com/java-completablefuture"
  (:refer-clojure :exclude [future])
  (:require
    [blaze.anomaly :as ba]
    [clojure.math :as math]
    [cognitect.anomalies :as anom]
    [taoensso.timbre :as log])
  (:import
    [java.util.concurrent CompletionStage CompletableFuture TimeUnit CompletionException]
    [java.util.function BiConsumer Function BiFunction Supplier]))


(set! *warn-on-reflection* true)


(defn completable-future? [x]
  (instance? CompletableFuture x))


(defn future
  "Returns a new incomplete CompletableFuture"
  []
  (CompletableFuture.))


(defn completed-future
  "Returns a CompletableFuture that is already completed with `x` or completed
  exceptionally if `x` is an anomaly."
  [x]
  (if (ba/anomaly? x)
    (CompletableFuture/failedFuture (ba/ex-anom x))
    (CompletableFuture/completedFuture x)))


(defn failed-future
  "Returns a CompletableFuture that is already completed exceptionally with the
  exception `e`"
  [e]
  (CompletableFuture/failedFuture e))


(defn all-of
  "Returns a CompletableFuture that is completed when all of `futures` complete.

  If any of the given futures complete exceptionally, then the returned
  CompletableFuture also does so, with a CompletionException holding this
  exception as its cause. Otherwise, the results, if any, of the given
  CompletableFutures are not reflected in the returned CompletableFuture, but
  may be obtained by inspecting them individually.

  If no CompletableFutures are provided, returns a CompletableFuture completed
  with nil."
  [futures]
  (CompletableFuture/allOf (into-array CompletableFuture futures)))


(defn complete!
  "If not already completed, sets the value of `future` to `x`.

  Returns true if this invocation caused `future` to transition to a completed
  state, else false."
  [future x]
  (.complete ^CompletableFuture future x))


(defn or-timeout!
  "Exceptionally completes `future` with a TimeoutException if not otherwise
  completed before `timeout` in `unit`.

  Returns `future` itself."
  [future timeout unit]
  (.orTimeout ^CompletableFuture future timeout unit))


(defn complete-on-timeout!
  "Completes `future` with `x` if not otherwise completed before `timeout` in
  `unit`.

  Returns `future` itself."
  [future x timeout unit]
  (.completeOnTimeout ^CompletableFuture future x timeout unit))


(defn complete-exceptionally!
  "If not already completed, causes invocations of `get` and related methods to
  throw `e`.

  Returns true if this invocation caused `future` to transition to a completed
  state, else false."
  [future e]
  (.completeExceptionally ^CompletableFuture future e))


(defn delayed-executor
  "Returns a new Executor that submits a task to the default executor after
  `delay` in `unit`.

  Each delay commences upon invocation of the returned executor's `execute`
  method."
  [delay unit]
  (CompletableFuture/delayedExecutor delay unit))


(defn join
  "Like `clojure.core/deref` but faster."
  [future]
  (.join ^CompletableFuture future))


(defn done?
  "Returns true if `future` completed either: normally, exceptionally, or via
  cancellation."
  [future]
  (.isDone ^CompletableFuture future))


(defn canceled?
  "Returns true if `future` was cancelled before it completed normally."
  [future]
  (.isCancelled ^CompletableFuture future))


(defn cancel!
  "If not already completed, completes `future` with a CancellationException.

  Dependent CompletableFutures that have not already completed will also
  complete exceptionally, with a CompletionException caused by this
  CancellationException.

  Returns true if `future` is now cancelled."
  [future]
  (.cancel ^CompletableFuture future false))


(defn- ->Supplier [f]
  (reify Supplier
    (get [_]
      (ba/throw-when (f)))))


(defn supply-async
  "Returns a CompletableFuture that is asynchronously completed by a task
  running in `executor` with the value obtained by calling the function `f`
  with no arguments."
  ([f]
   (CompletableFuture/supplyAsync (->Supplier f)))
  ([f executor]
   (CompletableFuture/supplyAsync (->Supplier f) executor)))


(defn completion-stage? [x]
  (instance? CompletionStage x))


(defn- ->Function [f]
  (reify Function
    (apply [_ x]
      (ba/throw-when (f x)))))


(defn then-apply
  "Returns a CompletionStage that, when `stage` completes normally, is executed
  with `stage`'s result as the argument to the function `f`."
  [stage f]
  (.thenApply
    ^CompletionStage stage
    (->Function f)))


(defn then-apply-async
  "Returns a CompletionStage that, when `stage` completes normally, is executed
  using the optional `executor`, with `stage`'s result as the argument to the
  function `f`."
  ([stage f]
   (.thenApplyAsync
     ^CompletionStage stage
     (->Function f)))
  ([stage f executor]
   (.thenApplyAsync
     ^CompletionStage stage
     (->Function f)
     executor)))


(defn then-compose
  "Returns a CompletionStage that is completed with the same value as the
  CompletionStage returned by the function `f`.

  When `stage` completes normally, the function `f` is invoked with `stage`'s
  result as the argument, returning another CompletionStage. When that stage
  completes normally, the CompletionStage returned by this method is completed
  with the same value.

  To ensure progress, the function `f` must arrange eventual completion of its
  result."
  [stage f]
  (.thenCompose
    ^CompletionStage stage
    (->Function f)))


(defn then-compose-async
  "Returns a CompletionStage that is completed with the same value as the
  CompletionStage returned by the function `f`, executed using the optional
  `executor`.

  When `stage` completes normally, the function `f` is invoked with `stage`'s
  result as the argument, returning another CompletionStage. When that stage
  completes normally, the CompletionStage returned by this method is completed
  with the same value.

  To ensure progress, the function `f` must arrange eventual completion of its
  result."
  ([stage f]
   (.thenComposeAsync
     ^CompletionStage stage
     (->Function f)))
  ([stage f executor]
   (.thenComposeAsync
     ^CompletionStage stage
     (->Function f)
     executor)))


(defprotocol CompletionCause
  (-completion-cause [e]))


(extend-protocol CompletionCause
  CompletionException
  (-completion-cause [e] (.getCause e))
  Throwable
  (-completion-cause [e] e))


(defn handle
  "Returns a CompletionStage that, when `stage` completes either normally or
  exceptionally, is executed with `stage`'s result and exception as arguments to
  the function `f`.

  When `stage` is complete, the function `f` is invoked with the result (or nil
  if none) and the anomaly (or nil if none) of `stage` as arguments, and the
  `f`'s result is used to complete the returned stage."
  [stage f]
  (.handle
    ^CompletionStage stage
    (reify BiFunction
      (apply [_ x e]
        (ba/throw-when (f x (some-> e -completion-cause ba/anomaly)))))))


(defn exceptionally
  "Returns a CompletionStage that, when `stage` completes exceptionally, is
  executed with `stage`'s anomaly as the argument to the function `f`.
  Otherwise, if `stage` completes normally, then the returned stage also
  completes normally with the same value."
  [stage f]
  (.exceptionally
    ^CompletionStage stage
    (reify Function
      (apply [_ e]
        (ba/throw-when (f (ba/anomaly (-completion-cause e))))))))


(defn exceptionally-compose [stage f]
  (-> stage
      (handle
        (fn [_ e]
          (if (nil? e)
            stage
            (f e))))
      (then-compose identity)))


(defn when-complete
  "Returns a CompletionStage with the same result or exception as `stage`, that
  executes the given action when `stage` completes.

  When `stage` is complete, the given action is invoked with the result (or nil
  if none) and the exception (or nil if none) of `stage` as arguments. The
  returned stage is completed when the action returns."
  [stage f]
  (.whenComplete
    ^CompletionStage stage
    (reify BiConsumer
      (accept [_ x e]
        (f x e)))))


(defn when-complete-async
  "Returns a CompletionStage with the same result or exception as `stage`, that
  executes the given action using `executor` when `stage` completes.

  When `stage` is complete, the given action is invoked with the result (or nil
  if none) and the exception (or nil if none) of `stage` as arguments. The
  returned stage is completed when the action returns."
  [stage f executor]
  (.whenCompleteAsync
    ^CompletionStage stage
    (reify BiConsumer
      (accept [_ x e]
        (f x e)))
    executor))


(defn ->completable-future [stage]
  (.toCompletableFuture ^CompletionStage stage))


(defmacro do-sync
  "Returns a CompletionStage that, when `stage-form` completes normally,
  executes `body` with `stage-forms`'s result bound to `binding-form`."
  [[binding-form stage-form] & body]
  `(then-apply
     ~stage-form
     (fn [~binding-form]
       ~@body)))


(defn- retryable? [{::anom/keys [category]}]
  (#{::anom/not-found ::anom/busy} category))


(defn- retry* [future-fn max-retries num-retry]
  (-> (future-fn)
      (exceptionally-compose
        (fn [e]
          (if (and (retryable? e) (< num-retry max-retries))
            (let [delay (* (long (math/pow 2.0 num-retry)) 100)]
              (log/warn (format "Wait %d ms before retrying an action." delay))
              (-> (future)
                  (complete-on-timeout!
                    nil delay TimeUnit/MILLISECONDS)
                  (then-compose
                    (fn [_] (retry* future-fn max-retries (inc num-retry))))))
            e)))))


(defn retry
  "Please be aware that `num-retries` shouldn't be higher than the max stack
  depth. Otherwise, the CompletionStage would fail with a StackOverflowException."
  [future-fn num-retries]
  (retry* future-fn num-retries 0))
