import cv2
import pilecv4j
import numpy as np

def func(kogsys):
    imageSource = kogsys.getImageSource()
    km = imageSource.next()
    while (km is not None):
        img = km.get()
        if (km.isRgb()):
            img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        img = cv2.flip(img, -1)
        km.setResult(img)
        km = imageSource.next()

