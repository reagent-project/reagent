(ns geometry)

(defprotocol IPoint 
  (x [p])
  (y [p]))

(deftype Point [x-coord y-coord]
  IPoint
  (x [_] x-coord)
  (y [_] y-coord))

(defn point [x y]
  (Point. x y))

(defn addp [p1 p2]
  (point (+ (x p1) (x p2))
         (+ (y p1) (y p2))))

(defn rand-point [xmin xmax ymin ymax]
  (point (rand-nth (vec (range xmin xmax)))
         (rand-nth (vec (range ymin ymax)))))

(defn rand-dir []
  (rand-point -1 2 -1 2))


(defn dist [p1 p2]
  (js/Math.sqrt (+ (js/Math.pow (- (x p2) (x p1)) 2)
                   (js/Math.pow (- (y p2) (y p1)) 2))))

