"""Plain-Spanish names and explanations for every setting (the user asked for options "mucho más fáciles de entender"
on 25/11/2026). Each setting gets its own vd_p_* strings: Spanish in values-es, and in values the same English text
as before through a reference to the old string, so other languages keep their translations' meaning.

Run from the codigo folder: python scripts/textos-ajustes.py
"""
import os
import re

RES = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res')

# key: (name, explanation or None to keep the current one). Only settings that already had an explanation in the
# XML get the new one, since the others show their chosen value there.
SETTINGS = {
    # Apariencia y búsqueda
    'app_language': ('Idioma', None),
    'ytdlnis_theme': ('Modo claro u oscuro', None),
    'ytdlnis_icon': ('Ícono de la app', None),
    'theme_accent': ('Color principal', None),
    'high_contrast': ('Contraste alto', 'Fondo negro puro y letras blancas, más fácil de leer'),
    'hide_thumbnails': ('Ocultar las imágenes de los videos', None),
    'modify_download_card': ('Qué mostrar al descargar', None),
    'display_over_apps': ('Mostrar encima de otras apps', 'La ventana de descarga aparece sobre la app desde la que compartiste. Actívalo si esa app se cierra al compartir.'),
    'recommendations_home': ('Videos sugeridos en Inicio', 'Muestra videos recomendados de YouTube en Inicio'),
    'api_key': ('Clave de YouTube (opcional)', 'Hace falta una clave propia de YouTube para ver sus sugerencias'),
    'youtube_data_fetching_extractor': ('Cómo leer los datos de YouTube', None),
    'formats_source': ('De dónde sacar las calidades', None),
    'use_itemurl_instead_playlisturl': ('Enlace propio para cada video de una lista', 'Si está activado, el nombre de la lista no se guarda en los archivos'),
    'history_quick_action': ('Botón rápido en Archivos', None),
    'locale': ('País para las búsquedas', None),
    'label_visibility': ('Nombres debajo de los íconos de la barra', None),
    'search_engine': ('Dónde buscar', 'El sitio que se usa cuando escribes una búsqueda en vez de un enlace'),
    'check_clipboard_home': ('Detectar enlaces copiados', 'Si copiaste un enlace, Inicio te ofrece usarlo'),
    'search_suggestions': ('Sugerencias al buscar', 'Muestra sugerencias de Google mientras escribes'),
    'swipe_gesture': ('Deslizar para acciones rápidas', 'Desliza un elemento hacia un costado para borrarlo o descargarlo'),
    'swipe_gestures_download_card': ('Deslizar en la ventana de descarga', None),
    'show_terminal': ('Terminal en el menú Compartir', None),
    'show_quick_download_share': ('«Descargar ya» en el menú Compartir', None),
    'show_count_downloads': ('Mostrar cuántas descargas hay en la fila', None),
    'write_ytdlp_terminal': ('Escribir «yt-dlp» solo en la terminal', None),
    'ignore_battery': ('Que el ahorro de batería no corte las descargas', None),
    # Descargas
    'incognito': ('Modo incógnito', 'Descarga sin guardar nada en tu historial'),
    'download_card': ('Elegir antes de descargar', 'Muestra la ventana para elegir calidad y formato. Si la apagas, la descarga empieza enseguida.'),
    'quick_download': ('Descargar sin esperar los datos', 'Empieza a bajar sin leer antes el título, la imagen y las calidades'),
    'prevent_duplicate_downloads': ('No bajar dos veces lo mismo', None),
    'download_delay': ('Pausa entre descargas', 'Espera unos segundos entre una descarga y otra. Así se baja de a una.'),
    'download_archive_path': ('Carpeta del registro de descargas', None),
    'metered_networks': ('Descargar con datos móviles', 'Si está apagado, espera a tener Wi-Fi'),
    'force_ipv4': ('Usar solo IPv4', 'Puede ayudar si las descargas fallan en algunas redes'),
    'use_cookies': ('Usar cookies (sesiones iniciadas)', None),
    'proxy': ('Proxy', 'Pasa las descargas por otro servidor (dirección HTTP, HTTPS o SOCKS)'),
    'preferred_download_type': ('Qué se abre primero: video o audio', 'La pestaña con la que se abre la ventana de descarga'),
    'remember_download_type': ('Recordar video o audio', 'Abre la ventana de descarga en lo último que elegiste'),
    'concurrent_fragments': ('Partes que se bajan a la vez', 'Más partes a la vez puede bajar algunos videos más rápido'),
    'concurrent_downloads': ('Descargas a la vez', 'Cuántos videos se bajan al mismo tiempo'),
    'limit_rate': ('Límite de velocidad', 'Velocidad máxima de cada descarga, por ejemplo 500K o 2M. Vacío: sin límite'),
    'buffer_size': ('Memoria de descarga', 'Cuánto junta en memoria antes de escribir el archivo, por ejemplo 16K (normal: 1024)'),
    'socket_timeout': ('Tiempo de espera de la conexión', 'Segundos que espera una respuesta antes de darse por vencido'),
    'log_downloads': ('Guardar un registro de cada descarga', 'Sirve para averiguar por qué falló una descarga'),
    'cleanup_leftover_downloads': ('Borrar restos de descargas canceladas o con error', None),
    'retries': ('Reintentos si falla', None),
    'fragment_retries': ('Reintentos de cada parte', None),
    'use_alarm_for_scheduling': ('Despertador para los horarios', 'Actívalo si las descargas programadas no arrancan a tiempo: algunos teléfonos las frenan'),
    'use_scheduler': ('Descargar solo en un horario', None),
    'schedule_start': ('Desde', None),
    'schedule_end': ('Hasta', None),
    # Carpetas y nombres
    'music_path': ('Carpeta de audio', None),
    'video_path': ('Carpeta de videos', None),
    'command_path': ('Carpeta de la terminal', None),
    'cache_path': ('Carpeta temporal', None),
    'access_all_files': ('Permitir guardar en cualquier carpeta', 'Para elegir carpetas que Android bloquea al principio'),
    'check_available_storage': ('Revisar el espacio libre antes de bajar', None),
    'cache_downloads': ('Bajar primero a la memoria interna', 'Descarga en el teléfono y después mueve el archivo a tu carpeta. Úsalo si guardas en una tarjeta SD que da errores.'),
    'no_part': ('Escribir directo en el archivo final', 'No usa archivos .part mientras descarga'),
    'keep_cache': ('Guardar las partes descargadas', 'No borra los archivos temporales al terminar'),
    'file_name_template': ('Nombre de los videos', None),
    'file_name_template_audio': ('Nombre de los audios', None),
    'save_subdirectory': ('Ordenar en subcarpetas', 'Crea carpetas según el canal, la lista u otros datos del video'),
    'trim_filenames': ('Acortar los nombres largos', None),
    'restrict_filenames': ('Nombres simples, sin acentos ni espacios', 'Usa solo letras comunes, sin «&amp;» ni espacios. Útil para computadoras o teles viejas'),
    'temporary_files': ('Archivos temporales', None),
    # Video y audio
    'use_sponsorblock': ('Saltar publicidad del youtuber (SponsorBlock)', None),
    'sponsorblock_url': ('Servidor de SponsorBlock', None),
    'mtime': ('Usar la fecha original del video', 'El archivo queda con la fecha de publicación en vez de la de descarga'),
    'write_description': ('Guardar la descripción', 'Guarda el texto del video en un archivo aparte'),
    'use_extra_commands': ('Usar comandos extra', 'Suma tus comandos extra a las descargas de video y audio'),
    'force_keyframes': ('Cortes exactos', 'Al cortar un tramo, corta justo donde pediste. Tarda más.'),
    'embed_metadata': ('Guardar título, autor y fecha dentro del archivo', 'Así se ven en la galería y en los reproductores'),
    'audio_bitrate': ('Calidad del audio', None),
    'embed_thumbnail': ('Poner la portada en el audio', 'La imagen del video queda como tapa del audio'),
    'crop_thumbnail': ('Portada cuadrada', 'Recorta la imagen en cuadrado, como la tapa de un disco'),
    'embed_lyrics': ('Agregar la letra de la canción', 'Busca la letra en lrclib.net (un sitio externo) y la guarda dentro del audio'),
    'save_lrc': ('Guardar la letra con tiempos (.lrc)', 'Un archivo aparte con la letra que acompaña la música'),
    'playlist_as_album': ('Usar el nombre de la lista como álbum', 'Si el audio no tiene álbum, usa el nombre de la lista de reproducción'),
    'audio_language': ('Idioma del audio', None),
    'audio_codec': ('Formato interno del audio (códec)', None),
    'prefer_container_over_codec_audio': ('Elegir primero por tipo de archivo', 'Prioriza el tipo de archivo (mp3, m4a…) por sobre el códec'),
    'audio_format': ('Tipo de archivo de audio', None),
    'format_id_audio': ('Calidad fija por número (ID)', 'Si el video tiene una calidad con este número, se elige sola'),
    'prefer_drc_audio': ('Preferir audio con volumen nivelado (DRC)', None),
    'embed_subtitles': ('Poner los subtítulos dentro del video', 'Se pueden prender y apagar en el reproductor'),
    'write_subtitles': ('Guardar los subtítulos aparte', 'Crea un archivo de subtítulos junto al video'),
    'write_auto_subtitles': ('Guardar también los subtítulos automáticos', 'Los que YouTube genera solo, en un archivo aparte'),
    'no_keep_subs': ('Borrar el archivo de subtítulos después', 'Si los pusiste dentro del video, no deja el archivo suelto'),
    'subs_lang': ('Idiomas de los subtítulos', None),
    'sub_format': ('Formato de los subtítulos', None),
    'write_thumbnail': ('Guardar la imagen del video', 'Guarda la miniatura como imagen en la carpeta de descarga'),
    'video_embed_thumbnail': ('Poner la imagen como portada del video', 'La miniatura queda como tapa del archivo'),
    'thumbnail_format': ('Formato de la imagen', None),
    'add_chapters': ('Capítulos en los videos', 'Marca los capítulos de YouTube y las partes de SponsorBlock dentro del video'),
    'video_format': ('Tipo de archivo de video', None),
    'recode_video': ('Convertir el video', 'Cambia el video al tipo de archivo elegido. Tarda más.'),
    'compatible_video': ('Video compatible con todo', 'Lo convierte para que se vea en cualquier app o tele. Tarda más.'),
    'video_codec': ('Formato interno del video (códec)', None),
    'video_quality': ('Calidad de video', None),
    'format_id': ('Calidad fija por número (ID)', 'Si el video tiene una calidad con este número, se elige sola'),
    'internal_plugin_ffmpeg_preset': ('Velocidad de conversión', 'Más rápido da archivos más grandes; más lento, más chicos. Se usa al poner subtítulos y en otros arreglos.'),
    'remove_audio': ('Quitar el sonido', None),
    'also_download_audio': ('Bajar también el audio aparte', None),
    'preferred_format_size': ('Tamaño preferido del video', None),
    'preferred_format_size_audio': ('Tamaño preferido del audio', None),
    # Motor de descargas
    'packages': ('Paquetes (motores extra)', None),
    'apk_install_method': ('Cómo instalar las actualizaciones', None),
    'apk_install_external_apk_id': ('App para instalar', None),
    'format_order': ('Ordenar las calidades por', None),
    'format_filter': ('Qué calidades mostrar', None),
    'update_formats': ('Leer las calidades enseguida', 'Busca las calidades disponibles apenas se abre la ventana de descarga'),
    # Avanzado
    'yt_player_client': ('Modo de conexión con YouTube', 'Cómo se presenta la app ante YouTube (clientes y token PO)'),
    'generate_po_tokens': ('Generar tokens PO de YouTube', None),
    'use_app_language_for_metadata': ('Datos en el idioma de la app', 'Títulos y descripciones en tu idioma cuando YouTube los tiene traducidos. También puede cambiar la calidad elegida.'),
    'youtube_other_extractor_args': ('Parámetros extra para YouTube', None),
    'enable_data_fetching_extra_commands': ('Usar plantillas al leer datos', 'Suma las plantillas marcadas cuando la app lee los datos de un enlace'),
    'use_format_sorting': ('Elegir calidad por prioridades', None),
    'format_importance_video': ('Prioridades para elegir el video', None),
    'format_importance_audio': ('Prioridades para elegir el audio', None),
    'disable_write_info_json': ('No guardar los datos del video', 'Si una descarga se corta y sigue, vuelve a pedir los datos a internet (no recomendado)'),
    'no_check_certificates': ('No verificar la seguridad HTTPS (peligroso)', None),
    'use_header': ('Usar otro User-Agent', None),
    'no_flat_playlist': ('Leer cada video de las listas', 'Más lento, pero trae más datos de cada video'),
    'use_original_url_playlist': ('Usar el enlace original de la lista', 'Usa el enlace que pegaste en vez del que da el sitio'),
    # Aplicación y yt-dlp
    'update_app': ('Buscar actualizaciones', 'Al abrir la app, revisa si hay una versión nueva en GitHub'),
    'changelog': ('Novedades de cada versión', None),
    'ytdlp_source_label': ('Versión de otra fuente', None),
    'auto_update_ytdlp': ('Actualizar yt-dlp solo', 'Conviene dejarlo prendido: los sitios cambian seguido y yt-dlp se actualiza para seguir funcionando'),
    'update_ytdlp_while_downloading': ('Actualizar yt-dlp antes de cada descarga', 'Busca la última versión justo antes de bajar'),
    # Archivos temporales
    'temp_unfinished_downloads': ('Descargas sin terminar', 'Partes guardadas para poder seguir una descarga cortada'),
    'temp_info_jsons': ('Datos de los videos', 'Información guardada para no volver a pedirla a internet'),
    'temp_ytdlp_cache': ('Memoria de yt-dlp', 'Datos que yt-dlp reutiliza, como los de YouTube'),
    'temp_configs': ('Configuraciones temporales', 'Archivos cortos de comandos y listas de enlaces'),
    'temp_downloaded_installers': ('Instaladores descargados', 'Actualizaciones de la app y de los paquetes ya bajadas'),
    'move_cache': ('Mover a la carpeta de descargas', 'Pasa a tu carpeta los archivos que quedaron en la temporal'),
    'clear_all_temporary_files': ('Borrar todo', None),
    # every page
    'reset_preferences': ('Restablecer esta pantalla', 'Vuelve todas estas opciones a como venían con la app'),
}

# section titles, by the string they used: same new name on every page
SECTIONS = {
    'appearance': 'Cómo se ve',
    'misc': 'Otras opciones',
    'scheduling': 'Horarios',
    'general': 'Para todas las descargas',
    'format': 'Calidades',
    'command_templates': 'Plantillas de comandos',
    'update_channel': 'Tipo de versiones',
    'settings': 'Opciones',
}

PAGES = ['general_preferences', 'downloading_preferences', 'folders_preference', 'processing_preferences', 'updating_preferences',
         'advanced_preferences', 'app_update_preferences', 'ytdlp_update_preferences', 'temporary_files_preferences']


def main():
    es, en = {}, {}
    for page in PAGES:
        p = os.path.join(RES, 'xml', page + '.xml')
        s = open(p, encoding='utf-8').read()

        def tag(m):
            t = m.group(0)
            key = re.search(r'(?:app|android):key="([^"]+)"', t)
            if 'PreferenceCategory' in m.group(1):
                title = re.search(r'((?:app|android):title=")@string/([A-Za-z0-9_]+)"', t)
                if title and title.group(2) in SECTIONS:
                    name = f'vd_ps_{title.group(2)}'
                    es[name], en[name] = SECTIONS[title.group(2)], f'@string/{title.group(2)}'
                    t = t.replace(title.group(0), f'{title.group(1)}@string/{name}"')
                return t
            if not key or key.group(1) not in SETTINGS:
                return t
            new_title, new_summary = SETTINGS[key.group(1)]
            k = key.group(1)
            title = re.search(r'((?:app|android):title=")@string/([A-Za-z0-9_]+)"', t)
            if title:
                es[f'vd_p_{k}'], en[f'vd_p_{k}'] = new_title, f'@string/{title.group(2)}'
                t = t.replace(title.group(0), f'{title.group(1)}@string/vd_p_{k}"')
            summary = re.search(r'((?:app|android):summary=")@string/([A-Za-z0-9_]+)"', t)
            if summary and new_summary:
                es[f'vd_p_{k}_s'], en[f'vd_p_{k}_s'] = new_summary, f'@string/{summary.group(2)}'
                t = t.replace(summary.group(0), f'{summary.group(1)}@string/vd_p_{k}_s"')
            return t

        s2 = re.sub(r'<([A-Za-z.]+)\b[^>]*?/?>', tag, s)
        if s2 != s:
            open(p, 'w', encoding='utf-8').write(s2)

    for folder, values in (('values-es', es), ('values', en)):
        p = os.path.join(RES, folder, 'strings_settings_texts.xml')
        body = ''.join(f'    <string name="{k}">{v}</string>\n' for k, v in sorted(values.items()))
        with open(p, 'w', encoding='utf-8', newline='\n') as f:
            f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Made by scripts/textos-ajustes.py: the names and explanations '
                    'of the settings pages. -->\n<resources>\n' + body + '</resources>\n')
    print(len(es), 'texts')


if __name__ == '__main__':
    main()
