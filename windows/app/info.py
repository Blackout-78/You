APP_NAME = "You"
VERSION = "1.0.0"

try:                                   # written by the build, absent when run from source
    from .build_info import BUILD, COMMIT
except ImportError:
    BUILD, COMMIT = "dev", "local"

VERSION_LINE = f"{APP_NAME} {VERSION} \u00B7 build {BUILD} \u00B7 {COMMIT}"
