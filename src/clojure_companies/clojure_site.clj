(ns clojure-companies.clojure-site)

(def company-csv-url "https://raw.githubusercontent.com/clojure/clojure-site/master/content/community/companies.csv")

(slurp company-csv-url)
