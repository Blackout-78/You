import sys

from app.info import APP_NAME


def _install_crash_log():
    """Any error is written to crash.log (next to the library) and shown in a box, then the app exits."""
    import traceback

    def hook(kind, value, tb):
        text = "".join(traceback.format_exception(kind, value, tb))
        try:
            from app.settings import data_dir
            log = data_dir() / "crash.log"
            log.write_text(text, encoding="utf-8")
        except Exception:
            log = None
        if sys.stderr is not None:          # the packaged .exe has no console
            sys.stderr.write(text)
        try:
            from PySide6.QtWidgets import QApplication, QMessageBox
            if QApplication.instance():
                QMessageBox.critical(None, "You error",
                                     (f"Saved to: {log}\n\n" if log else "") + text[-1500:])
        except Exception:
            pass

    sys.excepthook = hook


def _install_native_trace():
    """If the app freezes or dies without a normal error, Python's stack goes into native_crash.log."""
    import faulthandler
    try:
        from app.settings import data_dir
        handle = open(data_dir() / "native_crash.log", "w", encoding="utf-8")
        faulthandler.enable(file=handle, all_threads=True)
        faulthandler.dump_traceback_later(8, file=handle)     # fires only if startup is stuck
        return handle
    except Exception:
        return None


def main():
    _install_crash_log()
    _trace = _install_native_trace()
    demo = "--demo" in sys.argv

    if sys.platform == "win32":
        try:
            import ctypes
            # Lets Windows show our icon on the taskbar instead of Python's.
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("You.App")
        except Exception:
            pass

    from PySide6.QtGui import QIcon
    from PySide6.QtWidgets import QApplication

    from app.settings import Settings
    from app.theme import STYLESHEET
    from app.util import resource_path
    from app.window import MainWindow

    qt_app = QApplication(sys.argv)
    qt_app.setApplicationName(APP_NAME)
    qt_app.setStyleSheet(STYLESHEET)
    qt_app.setWindowIcon(QIcon(resource_path("assets/icon.ico")))

    settings = Settings()
    window = MainWindow(settings, demo=demo)
    if not demo and "--selftest" not in sys.argv and not settings.get("welcomed"):
        from app.personal import WelcomeDialog
        WelcomeDialog(settings, window).exec()          # first time only: picture, name, look
        settings.set("welcomed", True)
        window._apply_looks()
        window._apply_profile()
    window.show()
    if "--selftest" in sys.argv:          # used by the automatic build to check the .exe really starts
        from PySide6.QtCore import QTimer as _Timer

        def finish():
            for index in range(4):
                window._on_nav(index)
                qt_app.processEvents()
                window.grab()
            from app.settings import data_dir
            (data_dir() / "selftest_ok.txt").write_text("ok", encoding="utf-8")
            qt_app.quit()

        _Timer.singleShot(1500, finish)
    try:
        import faulthandler
        from PySide6.QtCore import QTimer
        QTimer.singleShot(0, faulthandler.cancel_dump_traceback_later)    # event loop alive: no freeze
    except Exception:
        pass
    sys.exit(qt_app.exec())


if __name__ == "__main__":
    main()
