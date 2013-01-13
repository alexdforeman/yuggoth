(ns yuggoth.routes.archives
  (:use compojure.core hiccup.element hiccup.form hiccup.util yuggoth.config)
  (:require [yuggoth.models.db :as db]             
            [noir.session :as session]
            [noir.response :as resp]
            [noir.util.cache :as cache]
            [yuggoth.util :as util]
            [yuggoth.views.layout :as layout]))

(defn make-list [date items]
  [:div 
   date
   [:hr]
   (into [:ul] 
         (for [{:keys [id time title public]} items] 
           [:li.archive 
            (link-to {:class "archive"} 
                     (str "/blog/" (str id "-" (url-encode title))) 
                     (str (util/format-time time "MMMM dd") " - " title))             
            (if (session/get :admin) 
              (form-to [:post "/archives"]
                       (hidden-field "post-id" id)
                       (hidden-field "visible" (str public))
                       [:span.submit (if public (text :hide) (text :show))]))]))])


(defn archives-by-date [archives]
  (reduce
    (fn [groups [date items]]
      (conj groups (make-list date items)))
    [:div]
    (->> archives
      (sort-by :time)
      reverse
      (group-by #(util/format-time (:time %) "yyyy MMMM")))))


(defn archives []
  (cache/cache!
    :archives
    (layout/common 
      (text :archives-title)
      (archives-by-date (db/get-posts nil false (session/get :admin))))))

(defn show-tag [tagname] 
  (layout/common
    tagname    
    (archives-by-date (db/posts-by-tag tagname))))

(defroutes archive-routes
  (GET "/archives" [] (archives))
  (POST "/archives" [post-id visible] 
        (do (db/post-visible post-id (not (Boolean/parseBoolean visible)))
            (resp/redirect "/archives")))
  (GET "/tag/:tagname" [tagname] (show-tag tagname)))
