from pathlib import Path

path = Path('app/src/main/java/com/lanu/music/MainActivity.kt')
s = path.read_text()

if 'import androidx.media3.common.PlaybackException' not in s:
    marker = 'import androidx.media3.common.MediaMetadata\n'
    if marker not in s:
        raise SystemExit('Media3 import marker not found; refusing unsafe rewrite')
    s = s.replace(marker, marker + 'import androidx.media3.common.PlaybackException\n', 1)

# Idempotent: the fallback may already be present in the source after a previous change.
if 'override fun onPlayerError(error: PlaybackException)' in s:
    path.write_text(s)
    print('Playback error fallback already hardened; no source rewrite needed')
    raise SystemExit(0)

old = '''    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateMiniPlayer() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updateMiniPlayer() }
    }
'''
new = '''    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateMiniPlayer() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { updateMiniPlayer() }
        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            val failedIndex = c.currentMediaItemIndex
            runOnUiThread {
                status.text = "Parça oynatılamadı; sıradaki güvenli kaynak deneniyor."
                if (failedIndex in 0 until c.mediaItemCount) {
                    c.removeMediaItem(failedIndex)
                    if (c.mediaItemCount > 0) c.play() else c.pause()
                } else {
                    c.pause()
                }
                updateMiniPlayer()
            }
        }
    }
'''

if old not in s:
    raise SystemExit('playerListener target not found; refusing unsafe rewrite')

s = s.replace(old, new, 1)
path.write_text(s)
print('Playback error fallback hardened')
