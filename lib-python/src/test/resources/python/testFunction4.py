
class MyClass:
    def __init__(self, x, y):
        self.x = x
        self.y = y
        
def simple():
    ret = MyClass(2,3)
    print("instance:", ret, flush=True)
    return ret


