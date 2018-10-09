(ns clojure-companies.core
  (:require [clojure-companies.gsheets :as gsheets]
            [clojure-companies.clojure-site :as cljsite]
            [clojure.set :as set]
            [clj-fuzzy.metrics :as fuzz]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(def site-data (cljsite/clojure-company-data))
(def sheet-data (gsheets/clojure-company-data))

(count site-data) ;; => 337
(count sheet-data) ;; => 221

(count
 (set/intersection (into #{} (map :company) site-data)
                   (into #{} (map :company) sheet-data)))
;; => 82

(count
 (set/difference (into #{} (map :company) site-data)
                 (into #{} (map :company) sheet-data)))
;; => 255

(def not-in-site (set/difference (into #{} (map :company) sheet-data)
                                 (into #{} (map :company) site-data)))

(count not-in-site)
;; => 132


(defn case-differences []
  (let [site-names (set (map :company site-data))
        site-names-lower (set (map (comp str/trim str/lower-case :company) site-data))
        sheet-names (map :company sheet-data)]
    (filter (fn [name]
              (and (contains? site-names-lower (-> name str/trim str/lower-case))
                   (not (contains? site-names name))))
            sheet-names)))

(case-differences)
;; => ()

(defn fuzzy-differences []
  (let [site-names (map :company site-data)
        sheet-names (map :company sheet-data)]
    (reverse (sort-by first (for [in site-names
                                  en sheet-names
                                  :when (not= in en)]
                              [(fuzz/jaro-winkler in en) in en])))))

(defn merged-data []
  (let [site-names (set (map :company site-data))]
    (reduce (fn [res {:keys [company url]}]
              (if (and (not (contains? site-names company)) url)
                (conj res {:company company :url (str " pass:[" url "]")})
                res))
            site-data
            sheet-data)))


(comment
  (with-open [writer (io/writer "/home/arne/github/clojure-site/content/community/companies.csv")]
    (csv/write-csv writer
                   (into [["Name" " URL"]]
                         (map (juxt :company :url))
                         (sort-by (fn [c] (str/lower-case (apply str (re-seq #"[0-9a-zA-Z]+" (:company c)))))
                                  (merged-data)))))



  (fuzzy-differences))
