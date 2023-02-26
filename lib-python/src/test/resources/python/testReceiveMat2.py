import cv2
import pilecv4j
import numpy as np

def func(kogsys):
    imageSource = kogsys.getImageSource()
    km = imageSource.next()
    while (km is not None):
        img = km.get()
        km.setResult(img)
        km = imageSource.next()

