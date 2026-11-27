# Facebook group posts (facebook.com/groups/…/permalink/…, often reached from a share/v/ link): since late 2026
# Facebook names the post's data "node_v2" instead of "node", and yt-dlp 2026.11.23 skips it, so it ends with
# "No video formats found!". This plugin renames the key in the page before yt-dlp's own Facebook extractor reads it.
# Video Downloader passes this folder with --plugin-dirs; drop it once yt-dlp reads "node_v2" itself.
from yt_dlp.extractor.facebook import FacebookIE


class FacebookNodeV2IE(FacebookIE, plugin_name='nodev2'):
    def _download_webpage(self, *args, **kwargs):
        webpage = super()._download_webpage(*args, **kwargs)
        if isinstance(webpage, str) and '"node_v2":' in webpage and '/groups/' in str(args[0] if args else ''):
            webpage = webpage.replace('"node_v2":', '"node":')
        return webpage
