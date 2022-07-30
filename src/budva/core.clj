(ns budva.core
  (:require [clojure.string :as str]))

;; Simple CRUD framework
;; * routing
;; * db
;; * model driven system sketch

;; 1. let's keep all state in one place
;; * separate state from functions
;; * all system procedures signature - (op sys req)

(defonce system (atom {}))

(swap! system assoc :id "myserver")

@system

;; what is http
;; handle(req)=> resp

;; program with generic datastructers
;; one data structure and 100 functions vs 10 x 10 - Alan Peril
;; schema is optional

;; Matrix.clj - everything is fn(data) => data

;; request
{:request-method :get
 :uri "/users/u1"
 :headers {"accept" "application/json"}}

;; response
{:status 200
 :body "Hello"}

(defn handle [sys req]
  {:status 200 :body (str "Hello from " (:uri req))})

(handle system {:uri "/" :request-method :get})
(handle system {:uri "/ups" :request-method :get})


(require '[org.httpkit.server :as server])
(require '[org.httpkit.client :as client])

(comment
  (def stop-srv (server/run-server (fn [req] (handle system req)) {:port 3333}))

  (-> @(client/get "http://localhost:3333")
      (update :body slurp))

  (defn handle [sys req] {:status 200 :body (pr-str req)})

  (stop-srv)

  )

;; json

(require '[cheshire.core :as json])

(json/generate-string {:name "Nikolai"})
(json/parse-string "{\"name\":\"Nikolai\"}" keyword)

(defn parse-request [req]
  (update req :body
          (fn [x]
            (when x
              (try (json/parse-string x keyword)
                   (catch Exception _e x))))))

(parse-request {:body "{\"a\":1}"})

(defn format-response [resp]
  (if-let [body (:body resp)]
    (assoc resp
           :body (json/generate-string body {:pretty true})
           :headers {"content-type" "application/json"})
    resp))

(format-response {:body {:a 1}})

(defn handle [sys req]
  {:status 200 :body (dissoc req :async-channel)})

(defn server-handle [sys req]
  (let [handle (get @sys :handle)]
    (format-response (handle system (parse-request req)))))

(swap! system assoc :handle handle)

(server-handle system {:body "{}"})

(defn start-server [sys port]
  (let [stop (server/run-server (fn [req] (server-handle sys req)) {:port port})]
    (swap! sys assoc :server stop)))

(defn stop-server [sys]
  (when-let [stop (get @sys :server)]
    (stop)))

(comment
  (start-server system 3333)
  @system

  (-> @(client/get "http://localhost:3333")
      :body
      json/parse-string)

  (stop-server system)

  )


;; routing

;; GET /<resource>
;; GET /<resource>/<id>

(defn uri-parts [uri]
  (rest (str/split uri #"/")))

(uri-parts "/users/u1")


(defn get-route [{uri :uri :as req}]
  (let [[res id :as parts] (uri-parts uri)]
    (cond
      (= 0 (count parts))
      {:method :welcome}

      (= 1 (count parts))
      {:method (keyword (str "list-" (first parts)))
       :params {}}

      (= 2 (count parts))
      {:method (keyword (str "one-of-" (first parts)))
       :params {:id id}}

      :else
      {:method :not-found})))


(get-route {:uri "/"})
(get-route {:uri "/users"})
(get-route {:uri "/users/u1"})

(get-route {:uri "/patients"})

(require '[matcho.core :as matcho])

(matcho/match*
 (get-route {:uri "/users"})
 {:method :list-users, :params {}})

(matcho/match*
 {:method :one-of-users, :params {:id "u1"}}
 {:method :list-users, :params {}})


;; multimethod
(defmulti op (fn [sys op req] op))

(defmethod op :default
  [sys x req]
  {:status 404
   :body (str (:request-method req) " " (:uri req) " -> " x " not found")})

(defmethod op :list-users
  [sys _ req]
  {:status 200 :body [{:id "u1"}]})

(defmethod op :one-of-users
  [sys _ {{id :id} :params}]
  {:status 200 :body [{:id id}]})

(op system :list-users {})

(op system :one-of-users {:params {:id "u1"}})

(op system :ups {})

(defn dispatch [system req]
  (let [{meth :method params :params} (get-route req)]
    (op system meth (assoc req :route-params params))))

(comment
  @system

  (dispatch system {:uri "/users"})
  (dispatch system {:uri "/users/u1"})
  (dispatch system {:uri "/something"})

  (swap! system assoc :handle dispatch)

  )

;; working with db

(require '[next.jdbc :as jdbc])

(comment
  ;; create datasource
  (def db
    (jdbc/get-datasource
     {:dbtype "postgres"
      :dbname "budva"
      :user "postgres"
      :password "postgres"
      :host "localhost"
      :port 5432}))

  ;; (jdbc/execute! db ["create database budva"])

  (jdbc/execute! db ["select 1 as one"])

  (jdbc/execute! db
                 ["create table if not exists
                   users (id serial primary key, resource jsonb)"])

  (jdbc/execute! db ["select * from \"users\""])

  (jdbc/execute!
   db ["insert into \"users\" (resource) values (?::jsonb) returning *"
       (json/generate-string {:name "niquola"})])

  (jdbc/execute!
   db ["select * from \"users\""])

  )

(defn init-table-sql [tbl-name]
  (format "create table if not exists \"%s\" (id serial primary key, resource jsonb)"
          (name tbl-name)))

(init-table-sql :users)
(init-table-sql :patients)

(defn init-table [db tbl-name]
  (jdbc/execute! db [(init-table-sql tbl-name)]))

(comment
  (init-table db :patients)
  (init-table db :users)

  (jdbc/execute! db
   ["select *
     from information_schema.tables
     where table_schema='public'"])

  )
(defn insert [db tbl resource]
  (jdbc/execute!
   db [(format "insert into \"%s\" (resource) values (?::jsonb) returning *"
               (name tbl))
       (json/generate-string resource)]))

(comment
  (insert db :users {:name "niquola"})
  (insert db :users {:name "ivan"})
  (insert db :users {:name "pavel"})


  (jdbc/execute!
   db ["select * from \"users\""])

  ;; ups PGobject!

  (jdbc/execute! db ["truncate table \"users\""])

  )

(import '[org.postgresql.util PGobject])

;; let's do coercing
;; some java interop
(defn coerce-res [res]
  (->> res
       (reduce
        (fn [acc [k v]]
          (assoc acc k
                 (if (instance? PGobject v)
                   (cheshire.core/parse-string (.getValue v) keyword)
                   v)))
        {})))

(comment

  (->> (jdbc/execute! db ["select * from \"users\""])
       (mapv coerce-res))

  )

(defn query [db query]
  (->> (jdbc/execute! db query)
       (mapv coerce-res)))

(comment
  (query db ["select * from \"users\""])
  (query db ["select * from \"users\" limit 1"])
  )


(require '[honey.sql :as sql])

;; honeysql - sql as data
(sql/format
 {:select :*
  :from :users
  :limit 1})

(sql/format
 {:select :*
  :from :users
  :order-by [[[:raw "resource->>'name'"]]]
  :where [:ilike [:raw "resource->>name"] "%pav%"]})


(comment

  (query db (sql/format {:select :* :from :users :limit 1}))
  )

;; original sql
(defn search [sys q]
  (if-let [db (:db @sys)]
    (query db (honey.sql/format q))
    (throw (Exception. "No db in system"))))

(defn save [sys tbl res]
  (insert (:db @sys) tbl res))


(comment
  (swap! system assoc :db db)

  @system

  (search system  {:select :* :from :users :limit 3})

  ;; expeceted fail
  (search  system
   {:select :*
    :from :users
    :where [:ilike [[:raw "resource->>name'"]] "%pav%"]})

  ;; return query with search
  (search  system
           {:select :*
            :from :users
            :where [:ilike [[:raw "resource->>'name'"]] "%pav%"]})

  (search system  {:select :*
                   :from :information_schema.tables
                   :order-by [:table_name]
                   :where [:= :table_schema "public"]
                   :limit 10})
  )

(defmethod op
  :list-users
  [sys _ req]
  {:body (search system  {:select :* :from :users :limit 10})
   :status 200})

;; search with count?
(search system  {:select :%count.* :from :users :limit 10})


(comment
  (reset! system {})

  (swap! system assoc :handle dispatch)

  (op system :list-users {})

  (dispatch system {:uri "/users"})

  )

;; data dsl

(def model
  {:users {:id   {:type :string :required true}
           :name {:required true :type :string}}

   :patients {:id {:type :string}
              :name {:type :string}}})

(swap! system assoc :model model)

(defn migrate-system [sys]
  (doseq [tbls (keys (:model @system))]
    (println :init tbls)
    (init-table (:db @sys) tbls)))

(comment
  (migrate-system system)

  (search system {:select [:id [[:raw "resource->>'name'"] :name]] :from :patients})

  (save system :patients {:name "John"})

  (dispatch system {:uri "/patients"})

  )

(defmethod op :list-patients
  [sys _ req]
  {:body (search system {:select :* :from :patients})})

;; TODO: generic search
;; TODO: validation

(require '[clojure.set])

(defn validate [data model]
  ;; walk trho model
  ;; validate data keys
  ;; TODO: impl
  )

(validate {:ups "extra" :name 2} {:name {:type :string}})

(validate {:name "name"} {:name {:type :string}})

