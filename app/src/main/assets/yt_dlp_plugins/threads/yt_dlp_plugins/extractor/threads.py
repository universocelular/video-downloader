# Threads posts (threads.com, threads.net): the videos of a post, read from its page as a browser without a session
# gets it. yt-dlp has no extractor of its own for Threads; Video Downloader passes this folder with --plugin-dirs.
import re

from yt_dlp.extractor.common import InfoExtractor
from yt_dlp.utils import ExtractorError, float_or_none, int_or_none, parse_duration, str_or_none, traverse_obj, url_or_none


class ThreadsIE(InfoExtractor):
    IE_NAME = 'threads'
    _VALID_URL = r'https?://(?:www\.)?threads\.(?:com|net)/(?:(?:@[\w.-]+/post|t)/(?P<id>[\w-]+)|share/(?P<share>[\w-]+))'
    # Threads puts the post's data in the page only when the request looks like a browser opening it
    _HEADERS = {
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none',
        'Sec-Fetch-User': '?1',
        'Upgrade-Insecure-Requests': '1',
    }

    def _real_extract(self, url):
        post_id, share = self._match_valid_url(url).group('id', 'share')
        webpage, handle = self._download_webpage_handle(url, post_id or share, headers=self._HEADERS)
        if not post_id:
            # A share link leads to the post itself
            post_url = self._search_regex(r'(https://www\.threads\.(?:com|net)/@[\w.-]+/post/[\w-]+)', handle.url, 'post', default=None)
            post_url = post_url or self._search_regex(
                r'<link[^>]+rel="canonical"[^>]+href="(https://www\.threads\.(?:com|net)/@[\w.-]+/post/[\w-]+)', webpage, 'post')
            return self.url_result(post_url, ThreadsIE)

        post = self._find_post(webpage, post_id)
        if not post:
            raise ExtractorError('Threads did not include this post in its page', expected=True)
        username = traverse_obj(post, ('user', 'username', {str_or_none}))
        caption = traverse_obj(post, ('caption', 'text', {str_or_none})) or ''
        info = {
            'title': caption.split('\n')[0][:120] or f'Threads @{username or post_id}',
            'description': caption or None,
            'uploader': username,
            'uploader_id': username,
            'channel': username,
            'timestamp': int_or_none(post.get('taken_at')),
            'like_count': int_or_none(post.get('like_count')),
            'webpage_url': f'https://www.threads.com/@{username}/post/{post_id}' if username else url,
        }
        source = self._video_source(post)
        parts = [self._media(media, f'{post_id}_{i}') for i, media in enumerate(source.get('carousel_media') or [], 1)]
        parts = [part for part in parts if part.get('formats')]
        if not source.get('carousel_media'):
            single = self._media(source, post_id)
            if not single.get('formats'):
                raise ExtractorError('This Threads post has no video', expected=True)
            return {**info, **single}
        if not parts:
            raise ExtractorError('This Threads post has no video', expected=True)
        if len(parts) == 1:
            return {**info, **parts[0], 'id': post_id}
        return self.playlist_result([{**info, **part} for part in parts], post_id, info['title'], info['description'])

    @staticmethod
    def _video_source(post):
        """The post itself, or the post whose video it shows: one linked inside its text, attached, reposted or quoted"""
        def has_video(media):
            return isinstance(media, dict) and any(
                item.get('video_versions') or item.get('video_dash_manifest')
                for item in [media, *(media.get('carousel_media') or [])] if isinstance(item, dict))

        app_info = post.get('text_post_app_info') or {}
        shared = app_info.get('share_info') or {}
        candidates = (post, app_info.get('linked_inline_media'), shared.get('quoted_attachment_post'),
                      shared.get('reposted_post'), shared.get('quoted_post'))
        return next((media for media in candidates if has_video(media)), post)

    def _find_post(self, webpage, post_id):
        """The post among the page's data blocks: the one with its code and its media"""
        def walk(node):
            if isinstance(node, dict):
                if node.get('code') == post_id and any(k in node for k in ('video_versions', 'carousel_media', 'image_versions2')):
                    return node
                children = node.values()
            elif isinstance(node, list):
                children = node
            else:
                return None
            for child in children:
                found = walk(child)
                if found:
                    return found
            return None

        for block in re.findall(r'<script type="application/json"[^>]*\bdata-sjs\b[^>]*>(.*?)</script>', webpage, re.S):
            if post_id not in block:
                continue
            found = walk(self._parse_json(block, post_id, fatal=False))
            if found:
                return found
        return None

    def _media(self, media, media_id):
        """Formats of one video: the plain files and the separate video and audio of the DASH manifest"""
        formats = [{
            'format_id': f'mp4-{version.get("type")}',
            'url': version['url'],
            'ext': 'mp4',
            'width': int_or_none(version.get('width')) or int_or_none(media.get('original_width')),
            'height': int_or_none(version.get('height')) or int_or_none(media.get('original_height')),
        } for version in traverse_obj(media, ('video_versions', lambda _, v: url_or_none(v['url'])))]
        manifest = media.get('video_dash_manifest')
        document = self._parse_xml(manifest, media_id, fatal=False) if manifest else None
        if document is not None:
            formats.extend(self._parse_mpd_formats(document, mpd_id='dash'))
        thumbnails = [{
            'url': candidate['url'],
            'width': int_or_none(candidate.get('width')),
            'height': int_or_none(candidate.get('height')),
        } for candidate in traverse_obj(media, ('image_versions2', 'candidates', lambda _, v: url_or_none(v['url'])))]
        # The length is only in the manifest
        length = self._search_regex(r'mediaPresentationDuration="([^"]+)"', manifest or '', 'duration', default=None)
        return {
            'id': media_id,
            'formats': formats,
            'thumbnails': thumbnails,
            'duration': float_or_none(media.get('video_duration')) or parse_duration(length),
        }
