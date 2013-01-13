(ns yuggoth.routes.blog
  (:use compojure.core         
        noir.util.route
        hiccup.form 
        hiccup.element 
        hiccup.util 
        yuggoth.config)
  (:require [markdown.core :as markdown] 
            [yuggoth.views.layout :as layout]
            [yuggoth.util :as util]
            [noir.session :as session]
            [noir.response :as resp]   
            [noir.util.cache :as cache]
            [yuggoth.models.db :as db]
            [yuggoth.routes.comments :as comments]))

(defn admin-forms [post-id visible]  
  (when (session/get :admin) 
    [:div
     [:div (form-to [:post "/toggle-post"]
                    (hidden-field "post-id" post-id)
                    (hidden-field "public" (str visible))
                    [:span.submit (if visible (text :hide) (text :show))])]
     [:div (form-to [:post "/update-post"]
                    (hidden-field "post-id" post-id)
                    [:span.submit (text :edit)])]]))


(defn post-nav [id]
  [:div
   (if (> id 1) [:div.leftmost (link-to (str "/blog-previous/" id) (text :previous))])
   (if (< id (db/last-post-id)) [:div.rightmost (link-to (str "/blog-next/" id) (text :next))])])

(defn display-public-post [postid next?]
  (resp/redirect 
    (if-let [id (db/get-public-post-id postid next?)]
      (str "/blog/" id)
      "/")))

(defn entry [{:keys [id time title content author public]} req]
   (apply layout/common         
         (if id
           [{:title title :elements (admin-forms id public)}
            [:p#post-time (util/format-time time)]
            (markdown/md-to-html-string content)
            (post-nav id)     
            [:br]
            [:br]
            [:div (str (text :tags) " ")
             (for [tag (db/tags-by-post id)]
              [:span.tagon {:id "tag"} tag])]
            
            (comments/get-comments id)            
            (comments/comment-form id (:context req))]
           [(text :empty-page) (text :nothing-here)])))

(defn tag-list [& [post-id]]
  (let [post-tags (set (if post-id (db/tags-by-post (Integer/parseInt post-id))))] 
    [:div
     (mapcat (fn [tag]
               (if (contains? post-tags tag)
                 [(hidden-field (str "tag-" tag) tag)
                  [:span.tagon tag]]
                 [(hidden-field (str "tag-" tag))
                  [:span.tagoff tag]]))
             (db/tags))
     (text-field {:placeholder (text :other)} "tag-custom")]))

(defn update-post [post-id error]
  (let [{:keys [title content public]} (db/get-post post-id)] 
    (layout/common
      (text :edit-post)
      (when error [:div.error error])
      (form-to [:post "/save-post"]
               (text-field {:tabindex 1} "title" title)
               [:br]
               (text-area {:tabindex 2} "content" content)
               [:br]
               (hidden-field "post-id" post-id)
               (hidden-field "public" (str public))               
               (str :tags " ") (tag-list post-id)
               [:br]
               [:span.submit {:tabindex 3} (text :post)]))))

(defn make-post [content error]  
  (layout/common
    (text :new-post)
    (when error [:div.error error])
    [:div#output]
    (form-to [:post "/save-post"]
             (text-field {:tabindex 1 :placeholder (text :title)} "title")
             [:br]
             (text-area {:tabindex 2} "content" content)
             [:br]
             "tags " (tag-list)
             [:br]
             [:div
              [:div.entry-public (text :publie)]
              (check-box {:tabindex 4} "public" true)                            
              [:div.entry-submit [:span.submit {:tabindex 3} "post"]]])))

(defn save-post [{:keys [post-id title content public] :as post}]  
  (if (not-empty title) 
    (let [tags (->> post (filter #(.startsWith (name (first %)) "tag-")) 
                 (map second) 
                 (remove empty?))]        
      (if post-id
        (do
          (db/update-post post-id title content public)
          (db/update-tags post-id tags))
        (db/update-tags 
          (:id (db/store-post title content (:handle (session/get :admin)) public))
          tags))
      
      (cache/invalidate! :home)
      (cache/invalidate! (str "post-" post-id))
      
      (resp/redirect (if post-id (str "/blog/" (str post-id "-" (url-encode title))) "/")))
    (make-post content (assoc post :error (text :title-required)))))

(defn home-page [req]   
  (if (:initialized @blog-config)
    (cache/cache! 
      :home 
      (if-let [post (db/get-last-public-post)] 
        (entry post req)
        (layout/common (text :welcome-title) (text :nothing-here))))
    (resp/redirect "/setup-blog")))

(defn about-page []
  (layout/common
   "this is the story of yuggoth... work in progress"))

(defroutes blog-routes   
  (GET "/blog-previous/:postid" [postid] (display-public-post postid false))
  (GET "/blog-next/:postid" [postid] (display-public-post postid true))
  (GET "/blog/:postid" [postid :as req] 
       (if-let [id (re-find #"\d+" postid)]
         (cache/cache! (str "post-" id) (entry (db/get-post id) req))
         (resp/redirect "/")))  
  (restricted GET "/make-post" [content error] (make-post content error))
  (restricted POST "/update-post" [post-id error] (update-post post-id error))
  (restricted POST "/save-post" {post :params} (save-post post))
  (restricted POST "/toggle-post" [post-id public] 
              (do (db/post-visible post-id (not (Boolean/parseBoolean public)))
                  (resp/redirect (str "/blog/" post-id))))
  (GET "/" req (home-page req)))