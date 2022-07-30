(ns budva.clojure)

;; Clojure in 5 min
;; how to read clojure

;; myfn(a,b,c) => (myfn a b c)
;; 1 + 1 => (+ 1 1)

;; numbers
1
100.0
1/3

;; bools
true false
(if true "true" "ok")

;; string

"Hello world"

(subs "Hello" 2)

;; keywords

:name

:human/name

;; symbols

'fn-name

'namespace/fn-name


;; collections

;; vector
(type [1 2 3])

[1 2 3 4]

(conj [1 2] 3)
(map inc [1 2 3])

;; set

#{:a :b :c}

(type #{:a :b :c})

(conj #{:a :b :c} :c)

(conj #{:a :b :c} :d)

(disj #{:a :b :c} :a)

;; list

(type '(1 2 3))

(conj '(1 2 3 4) 5)

(+ 1 2 3)

(read-string "(+ 1 2 3)")

;; map

{:a 1 :b 2}

(assoc {} :a 1)

(dissoc {:a 1} :a 2 :b 2)

(get-in {:a {:b {:c 1}}} [:a :b :c])

(vals {:a 1 :b 2})

(keys {:a 1 :b 2})

;; vars

(def a 1)

(type a)
(type #'a)

a

(def a 2)

;; functions

(def myfn (fn [x]
            (str "Hello " x)))

(defn myfn [x]
  (str "Hello " x))

(myfn "Budva")


;; other forms

(if (= 1 1) :eq :not-eq)

(when true (println "Hi"))

(try
  ;; do something
  (throw (Exception. "ups"))
  (catch Exception e
    (str e)))

;; interop

(type (java.util.Date.))


;; state

(def my-state (atom {}))

@my-state

(swap! my-state (fn [old] (assoc old :a 1)))

@my-state

(reset! my-state {})

(doseq [i (range 10)]
  ;; multithreads
  (future
    (doseq [j (range 100)]
      (swap! my-state (fn [x]
                        (update x :cnt
                                (fn [old] (+ (or old 0) (* i j)))))))))

@my-state



