import cv2
import pilecv4j
import numpy as np

def func(kogsys):
    imageSource = kogsys.getImageSource()
    km = imageSource.next()
    while (km is not None):
        params = km.getParams()
        print ("Params: ", params, flush=True)
        tmp = params.get('doesntexist')
        if (tmp is None):
          print("doesntexist Doesn't Exist")
        tmp = params.get('float')
        print ("float is ", tmp)
        img = km.get()
        if (km.isRgb()):
            img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        img = cv2.flip(img, -1)
        km.setResult(img)
        km = imageSource.next()

