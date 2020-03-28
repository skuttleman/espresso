(defproject com.ben-allred/espresso "0.2.0"
  :description "Espresso - A wrapper around NodeJS http module for composing web applications."
  :url "https://www.github.com/skuttleman/espresso"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.9.1"
  :dependencies [[com.ben-allred/vow "0.4.0"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.597"]]
  :plugins [[lein-figwheel "0.5.19"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]
  :source-paths ["src/cljs"]
  :clean-targets ["target"]
  :cljsbuild {:builds
              [{:id           "dev"
                :source-paths ["src/cljs"]
                :figwheel     true
                :compiler     {:install-deps         true
                               :npm-deps             {:ws "7.1.2"}
                               :main                 com.ben-allred.espresso.core
                               :asset-path           "target/js/compiled/dev"
                               :output-to            "target/js/compiled/espresso.js"
                               :output-dir           "target/js/compiled/dev"
                               :target               :nodejs
                               :optimizations        :none
                               :source-map-timestamp true}}
               {:id           "build"
                :source-paths ["src/cljs"]
                :compiler     {:output-to     "target/js/lib.js"
                               :main          com.ben-allred.espresso.core
                               :optimizations :advanced
                               :target        :nodejs
                               :pretty-print  false}}]}
  :profiles {:dev   {:dependencies [[binaryage/devtools "0.9.10"]
                                    [cider/piggieback "0.4.0"]
                                    [figwheel-sidecar "0.5.19"]]
                     :figwheel     {:nrepl-port 7888}
                     :source-paths ["src/cljs" "dev"]}})

