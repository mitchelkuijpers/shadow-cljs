(ns shadow.cljs.npm.babel-worker
  (:require
    [clojure.string :as str]
    [cljs.core.async :as async :refer (go)]
    [cljs.reader :refer (read-string)]
    ["babel-core" :as babel]
    ["babel-plugin-transform-es2015-modules-commonjs" :as babel-transform-esm]
    ))

;; why is this a plugin and not a config option?
(defn external-helpers-plugin [ref]
  (let [t (.. ref -types)
        id (.identifier t "global.shadow.js.babel")]
    #js {:pre #(.set % "helpersNamespace" id)}))

(defn babel-transform [{:keys [code resource-name]}]
  (let [presets
        #js []

        plugins
        #js [babel-transform-esm external-helpers-plugin]

        opts
        #js {:presets presets
             :plugins plugins
             :babelrc false
             :filename resource-name
             :highlightCode false
             :sourceMaps true}

        res
        (babel/transform code opts)]

    {:code (.-code res)
     :source-map-json (js/JSON.stringify (.-map res))
     :metadata (js->clj (.-metadata res) :keywordize-keys true)}
    ))

(defn process-request [line]
  (let [req
        (read-string line)

        #_{:code "class Foo {}; let x = 1; export { x }; export default x;"
           :resource-name "test.js"}
        res
        (babel-transform req)]
    (prn res)))

(defn process-chunk [buffer chunk]
  (let [nl (str/index-of chunk "\n")]
    (if-not nl
      ;; no newline, return new buffer
      (str buffer chunk)

      ;; did contain \n, concat with remaining buffer, handoff
      (let [line (str buffer (subs chunk 0 nl))]
        (process-request line)
        (recur "" (subs chunk (inc nl))))
      )))

(defn main [& args]
  (let [stdin
        (async/chan)

        main-loop
        (go (loop [buffer ""]
              (when-some [chunk (<! stdin)]
                (recur (process-chunk buffer chunk))
                )))

        stdin-data
        (fn stdin-data [buf]
          (let [chunk (.toString buf)]
            (async/put! stdin chunk)))]

    (js/process.stdin.on "data" stdin-data)
    (js/process.stdin.on "close" #(async/close! stdin))))
