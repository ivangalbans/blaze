(ns blaze.async.comp-test
  (:require
    [blaze.anomaly :as ba]
    [blaze.async.comp :as ac :refer [do-sync]]
    [blaze.async.comp-spec]
    [blaze.executors :as ex]
    [blaze.test-util :as tu :refer [given-failed-future]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [cognitect.anomalies :as anom])
  (:import
    [java.util.concurrent TimeUnit]))


(set! *warn-on-reflection* true)
(st/instrument)


(test/use-fixtures :each tu/fixture)


(deftest completed-future-test
  (testing "on completed future"
    (is (= ::x @(ac/completed-future ::x))))

  (testing "on exceptionally completed future"
    (given-failed-future (ac/completed-future (ba/fault ""))
      ::anom/category := ::anom/fault)))


(deftest failed-future-test
  (try
    @(ac/failed-future (ex-info "e" {::a ::b}))
    (catch Exception e
      (is (= "e" (ex-message (ex-cause e))))
      (is (= ::b (::a (ex-data (ex-cause e))))))))


(deftest all-of-test
  (testing "with two successful futures"
    (let [a (ac/future)
          b (ac/future)
          all (ac/all-of [a b])]
      (ac/complete! a 1)
      (ac/complete! b 2)

      (is (nil? @all))))

  (testing "with one failed future"
    (let [a (ac/future)
          b (ac/future)
          all (ac/all-of [a b])]
      (ac/complete! a 1)
      (ac/complete-exceptionally! b (ex-info "e" {}))

      (try
        @all
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))


(deftest or-timeout!-test
  (testing "with timeout happen"
    (let [f (ac/future)]
      (ac/or-timeout! f 1 TimeUnit/MILLISECONDS)
      (Thread/sleep 10)
      (is (= ::anom/busy @(ac/exceptionally f ::anom/category)))))

  (testing "without timeout happen"
    (let [f (ac/future)]
      (ac/or-timeout! f 10 TimeUnit/MILLISECONDS)
      (ac/complete! f ::b)
      (is (= ::b @f)))))


(deftest complete-on-timeout!-test
  (testing "with timeout happen"
    (let [f (ac/future)]
      (ac/complete-on-timeout! f ::a 1 TimeUnit/MILLISECONDS)
      (Thread/sleep 10)
      (ac/complete! f ::b)
      (is (= ::a @f))))

  (testing "without timeout happen"
    (let [f (ac/future)]
      (ac/complete-on-timeout! f ::a 10 TimeUnit/MILLISECONDS)
      (ac/complete! f ::b)
      (is (= ::b @f)))))


(deftest complete-exceptionally-test
  (let [f (ac/future)
        f' (ac/exceptionally f ::anom/message)]
    (ac/complete-exceptionally! f (ex-info "e" {}))
    (is (= "e" @f'))))


(deftest delayed-executor-test
  (let [f (ac/supply-async (constantly ::a) (ac/delayed-executor 100 TimeUnit/MILLISECONDS))]
    (is (not (ac/done? f)))
    (is (= ::a @f))))


(deftest join-test
  (testing "on completed future"
    (is (= ::x (ac/join (ac/completed-future ::x)))))

  (testing "on failed future"
    (try
      (ac/join (ac/failed-future (ex-info "e" {::a ::b})))
      (catch Exception e
        (is (= "e" (ex-message (ex-cause e))))
        (is (= ::b (::a (ex-data (ex-cause e)))))))))


(deftest supply-async-test
  (testing "successful"
    (is (= 1 @(ac/supply-async (constantly 1))))

    (testing "with executor"
      (is (= 1 @(ac/supply-async (constantly 1) (ex/single-thread-executor))))))

  (testing "error"
    (let [f (ac/supply-async (fn [] (throw (ex-info "e" {})))
                             (ex/single-thread-executor))]
      (try
        @f
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))

    (testing "with executor"
      (let [f (ac/supply-async (fn [] (throw (ex-info "e" {})))
                               (ex/single-thread-executor))]
        (try
          @f
          (catch Exception e
            (is (= "e" (ex-message (ex-cause e))))))))))


(deftest then-apply-test
  (let [f (ac/future)
        f' (ac/then-apply f inc)]
    (ac/complete! f 1)
    (is (= 2 @f'))))


(deftest then-apply-async-test
  (testing "with default executor"
    (let [f (ac/future)
          f' (ac/then-apply-async f inc)]
      (ac/complete! f 1)
      (is (= 2 @f'))))

  (testing "with custom executor"
    (let [f (ac/future)
          f' (ac/then-apply-async f inc (ex/single-thread-executor))]
      (ac/complete! f 1)
      (is (= 2 @f')))))


(deftest then-compose-test
  (let [f (ac/future)
        f' (ac/then-compose f ac/completed-future)]
    (ac/complete! f 1)
    (is (= 1 @f'))))


(deftest then-compose-async-test
  (testing "with default executor"
    (let [f (ac/future)
          f' (ac/then-compose-async f ac/completed-future)]
      (ac/complete! f 1)
      (is (= 1 @f'))))

  (testing "with custom executor"
    (let [f (ac/future)
          f' (ac/then-compose-async f ac/completed-future
                                    (ex/single-thread-executor))]
      (ac/complete! f 1)
      (is (= 1 @f')))))


(deftest handle-test
  (testing "with success"
    (let [f (ac/future)
          f' (ac/handle f (fn [x _] x))]
      (ac/complete! f 1)
      (is (= 1 @f'))))

  (testing "with error"
    (let [f (ac/future)
          f' (ac/handle f (fn [_ e] (::anom/message e)))]
      (ac/complete-exceptionally! f (ex-info "e" {}))
      (is (= "e" @f')))))


(deftest exceptionally-test
  (testing "the exception of a failed future will be converted to an anomaly"
    (is (= @(-> (ac/failed-future (Exception. "msg-125548"))
                (ac/exceptionally ::anom/message))
           "msg-125548")))

  (testing "the anomaly returned in a in-between stage shows up"
    (is (= @(-> (ac/completed-future "foo")
                (ac/then-apply (constantly (ba/fault "msg-131026")))
                (ac/exceptionally ::anom/message))
           "msg-131026"))))


(deftest cancel-test
  (let [f (ac/supply-async (constantly ::a) (ac/delayed-executor 100 TimeUnit/MILLISECONDS))]
    (is (not (ac/done? f)))
    (ac/cancel! f)
    (is (ac/canceled? f))
    (is (ac/done? f))))


(deftest when-complete-test
  (testing "with success"
    (let [f (ac/future)
          f' (ac/future)
          f'' (ac/when-complete f (fn [x _] (ac/complete! f' (inc x))))]
      (ac/complete! f 1)
      (is (= 2 @f'))
      (is (= 1 @f''))))

  (testing "with error"
    (let [f (ac/future)
          f' (ac/future)
          f'' (ac/when-complete f (fn [_ e] (ac/complete! f' (ex-message e))))]
      (ac/complete-exceptionally! f (ex-info "e" {}))
      (is (= "e" @f'))
      (try
        @f''
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))


(deftest when-complete-async-test
  (testing "with success"
    (let [f (ac/future)
          f' (ac/future)
          f'' (ac/when-complete-async f (fn [x _] (ac/complete! f' (inc x)))
                                      (ex/single-thread-executor))]
      (ac/complete! f 1)
      (is (= 2 @f'))
      (is (= 1 @f''))))

  (testing "with error"
    (let [f (ac/future)
          f' (ac/future)
          f'' (ac/when-complete-async f (fn [_ e] (ac/complete! f' (ex-message e)))
                                      (ex/single-thread-executor))]
      (ac/complete-exceptionally! f (ex-info "e" {}))
      (is (= "e" @f'))
      (try
        @f''
        (catch Exception e
          (is (= "e" (ex-message (ex-cause e)))))))))


(deftest ->completable-future-test
  (is (ac/completable-future? (ac/->completable-future (ac/future)))))


(deftest do-sync-test
  (testing "on normally completed future"
    (is (= 2 @(do-sync [x (ac/completed-future 1)] (inc x))))

    (testing "without body"
      (is (nil? @(do-sync [_ (ac/completed-future 1)])))))

  (testing "on normally exceptionally future"
    (given-failed-future (do-sync [x (ac/completed-future (ba/fault ""))] (inc x))
      ::anom/category := ::anom/fault)))


(deftest retry-test
  (testing "with first call successful"
    (let [future-fn #(ac/completed-future ::x)]
      (is (= ::x @(ac/retry future-fn 1)))))

  (testing "with second call successful"
    (testing "first call retryable"
      (let [counter (atom 0)
            future-fn #(ac/completed-future
                         (let [n (swap! counter inc)]
                           (if (= 2 n) ::x (ba/busy ""))))]
        (is (= ::x @(ac/retry future-fn 1)))))

    (testing "first call not retryable"
      (let [counter (atom 0)
            future-fn #(ac/completed-future
                         (let [n (swap! counter inc)]
                           (if (= 2 n) ::x (ba/fault ""))))]
        (given-failed-future (ac/retry future-fn 1)
          ::anom/category := ::anom/fault))))

  (testing "with third call successful"
    (testing "two retires"
      (let [counter (atom 0)
            future-fn #(ac/completed-future
                         (let [n (swap! counter inc)]
                           (if (= 3 n) ::x (ba/busy ""))))]
        (is (= ::x @(ac/retry future-fn 2)))))

    (testing "one retry"
      (let [counter (atom 0)
            future-fn #(ac/completed-future
                         (let [n (swap! counter inc)]
                           (if (= 3 n) ::x (ba/busy ""))))]
        (given-failed-future (ac/retry future-fn 1)
          ::anom/category := ::anom/busy))))

  (testing "times"
    (testing "with second call successful"
      (let [counter (atom 0)
            future-fn #(ac/completed-future
                         (let [n (swap! counter inc)]
                           (if (= 2 n) ::x (ba/busy ""))))
            start (System/nanoTime)]
        @(ac/retry future-fn 1)
        (is (< 1e8 (- (System/nanoTime) start)))))

    (testing "with third call successful"
      (let [counter (atom 0)
            future-fn #(ac/completed-future
                         (let [n (swap! counter inc)]
                           (if (= 3 n) ::x (ba/busy ""))))
            start (System/nanoTime)]
        @(ac/retry future-fn 2)
        (is (< 3e8 (- (System/nanoTime) start)))))

    (testing "with forth call successful"
      (let [counter (atom 0)
            future-fn #(ac/completed-future
                         (let [n (swap! counter inc)]
                           (if (= 4 n) ::x (ba/busy ""))))
            start (System/nanoTime)]
        @(ac/retry future-fn 3)
        (is (< 7e8 (- (System/nanoTime) start)))))))
