import pilecv4j

def func(kogsys):
    imageSource = kogsys.getImageSource()
    km = imageSource.next()
    while (km is not None):
        img = km.get()
        km.setResult(img)
        km = imageSource.next()

