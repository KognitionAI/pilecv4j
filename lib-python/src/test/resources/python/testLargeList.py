import pilecv4j


def make_floats(count):
    # Regression fixture for the result-serialization realloc bug: a list of
    # floats serializes to 9 bytes per element, so any count above ~900 forces
    # the native result buffer past RESULT_BLOCK_SIZE_INCREMENT (8192) and
    # exercises the realloc path inside the recursive write().
    return [float(i) * 0.5 for i in range(int(count))]
