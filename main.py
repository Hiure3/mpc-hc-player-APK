import os
import sys
import webview

# Em modo --windowed o PyInstaller não cria stdout/stderr; evita erros de log.
if sys.stdout is None:
    sys.stdout = open(os.devnull, 'w')
if sys.stderr is None:
    sys.stderr = open(os.devnull, 'w')


def resource_path(name):
    """Caminho do arquivo tanto no código-fonte quanto dentro do .exe."""
    base = getattr(sys, '_MEIPASS', os.path.dirname(os.path.abspath(__file__)))
    return os.path.join(base, name)


def main():
    # pywebview 5+ bloqueia downloads por padrão; necessário para salvar a gravação (.webm).
    try:
        webview.settings['ALLOW_DOWNLOADS'] = True
    except Exception:
        pass

    webview.create_window(
        'MPC-HC Media Player',
        resource_path('index.html'),
        width=980,
        height=640,
        min_size=(760, 520),
    )
    # http_server=True serve a página em http://127.0.0.1 (origem normal),
    # o que garante o funcionamento do processamento de áudio (Web Audio).
    webview.start(http_server=True)


if __name__ == '__main__':
    main()
