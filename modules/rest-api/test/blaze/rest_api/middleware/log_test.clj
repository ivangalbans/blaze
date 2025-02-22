(ns blaze.rest-api.middleware.log-test
  (:require
    [blaze.rest-api.middleware.log :refer [wrap-log]]
    [blaze.test-util :as tu]
    [blaze.test-util.ring :refer [call]]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(defn- handler [_ respond _]
  (respond ::x))


(deftest wrap-log-test
  (testing "without query string"
    (is (= ::x (call (wrap-log handler) {:request-method :get :uri "/foo"}))))

  (testing "with query string"
    (is (= ::x (call (wrap-log handler) {:request-method :get :uri "/foo" :query-string "bar"})))))
