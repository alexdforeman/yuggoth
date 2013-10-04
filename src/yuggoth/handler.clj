(ns yuggoth.handler
  (:use yuggoth.routes.admin
        yuggoth.routes.auth
        yuggoth.routes.archives
        yuggoth.routes.blog
        yuggoth.routes.page
        yuggoth.routes.comments
        yuggoth.routes.profile
        yuggoth.routes.rss
        compojure.core)
  (:require [yuggoth.config :as config]
            [yuggoth.views.layout :as layout]
            [noir.util.middleware :as middleware]
            [selmer.parser :as parser]
            [noir.response :as resp]
            [noir.session :as session]
            [noir.util.cache :as cache]
            [compojure.route :as route]))

(defroutes app-routes
  (route/resources "/")
  (route/not-found "Not Found"))

(defn init
  "init will be called once when
   app is deployed as a servlet on 
   an app server such as Tomcat
   put any initialization code here"
  []
  (parser/cache-off!)
  ;;add custom tags
  (parser/add-tag! :label
    (fn [[label-id] _]
      (config/text (keyword label-id))))
  
  (parser/add-tag! :clabel
    (fn [[label-id] _]
      (clojure.string/capitalize
        (config/text (keyword label-id)))))
  
  (config/init)
  (cache/set-size! 5)
  (println "yuggoth started successfully..."))

(defn admin-page [req]   
  (session/get :admin))

(defn wrap-ssl-if-selected [app]  
  (if (:ssl @config/blog-config)
    (fn [req]      
      (if (or (not-any? #(= (:uri req) (str (:context req) %)) ["/login"])
              (= :https (:scheme req))
              (= "https" ((:headers req) "x-forwarded-proto")))
        (app req)
        (let [host  (-> req
                        (:headers)
                        (get "host")
                        (clojure.string/split #":")
                        (first))              
              ssl-port (:ssl-port @config/blog-config)]          
          (resp/redirect (str "https://" host ":" ssl-port (:uri req)) :permanent))))    
    app))

(defn wrap-exceptions [app]
  (fn [request]
    (try 
      (app request)
      (catch Exception ex
        (.printStackTrace ex)
        {:status 500
         :headers {"Content-Type" "text/html"}
         :body (layout/common (config/text :welcome-title) 
                              (config/text :nothing-here))}))))

(def app (middleware/app-handler
           [auth-routes
            archive-routes
            comments-routes
            profile-routes
            rss-routes
            blog-routes
            page-routes
            admin-routes
            app-routes] 
           :middleware [wrap-exceptions
                        wrap-ssl-if-selected]
           :access-rules [admin-page]))

(def war-handler (middleware/war-handler app))
