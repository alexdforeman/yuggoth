(ns yuggoth.server
  (:use config)  
  (:require [noir.server :as server]
            [noir.core :as core]
            [noir.response :as resp]
            [noir.session :as session]            
            [yuggoth.views archives auth blog comments common profile rss upload])
  (:gen-class))

(defn secure-login-redirect [handler]
  (fn [request]
    (let [{:keys [scheme uri server-name server-port]} request]          
      (if (and (= scheme :http) (or (.contains uri "login") (.contains uri "create-admin")))      
        (ring.util.response/redirect (str "https://" server-name ":" (:ssl-port @blog-config) uri))
        (handler request)))))

(server/load-views-ns 'yuggoth.views)

(def params 
  {:mode :prod, 
   :ns 'yuggoth 
   :session-cookie-attrs {:max-age 1800000}})

(def handler
  (-> (server/gen-handler params)
    (#(if (:ssl @blog-config) (secure-login-redirect %) %))))


(defn parse-args [args]
  (into {} 
        (for [[name val] (partition 2 args)]
          (condp = name
            "-port" [:port (Integer/parseInt val)]
            "-mode" [:mode (if (some #{"dev" "prod"} [val])                             
                             (keyword val)
                             (throw (new Exception (str "unkown mode" val))))]            
             (throw (new Exception (str "invalid option " name val " see -help for valid options")))))))

(defn -main [& args]  
  (init-config)
  (if (= "-help" (first args))
    (println "valid options:\n-port integer\n-mode dev/prod\n-help this message")
    (let [m (parse-args args)
          mode (get m :mode :dev)         
          port (get m :port (new Integer 8080))]
      (println "starting in mode" mode " on port " port)
      (server/start port (assoc params :mode mode))))) 