(ns clojure-companies.clojure-site
  (:require [clojure.data.csv :as csv]))

(def company-csv-url "https://raw.githubusercontent.com/clojure/clojure-site/master/content/community/companies.csv")


(def renames {"BearyInnovative" "Beary Innovative"
              "CapitalOne" "Capital One"
              "RoomKey" "Room Key"
              "Deep Impact AG" "Deep Impact"
              "Oscaro.com" "Oscaro"
              "Kira Inc" "Kira"
              "Concur" "SAP Concur"})

(defn clojure-company-data []
  (map (fn [[c u]]
         {:company (renames c c)
          :url u})
       (next (csv/read-csv (slurp company-csv-url)))))

(comment
  (clojure-company-data))
