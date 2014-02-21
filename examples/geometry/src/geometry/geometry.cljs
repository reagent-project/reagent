(ns geometry.geometry)

(defprotocol IPoint 
  (x [p])
  (y [p]))

(deftype Point [x-coord y-coord]
  IPoint
  (x [_] x-coord)
  (y [_] y-coord))

(defn point [x y]
  (Point. x y))

(defn dist [p1 p2]
  (js/Math.sqrt (+ (js/Math.pow (- (x p2) (x p1)) 2)
                   (js/Math.pow (- (y p2) (y p1)) 2))))

