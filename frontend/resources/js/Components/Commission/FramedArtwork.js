import React from 'react';
import TextureCanvas from '@/Components/Commission/TextureCanvas';
import ThemeArt from '@/Components/Commission/ThemeArt';

const FRAME_STYLES = {
    NONE: { padding: 0, background: 'transparent', boxShadow: '8px 10px 0 -2px #d9d4cc, 18px 24px 36px rgba(0,0,0,.28)' },
    MATTE_BLACK: { padding: 18, background: 'linear-gradient(145deg,#2b2b2b,#111 55%,#1d1d1d)', boxShadow: 'inset 0 0 0 1px #000, inset 0 2px 6px rgba(255,255,255,.12), 0 22px 40px rgba(0,0,0,.35)' },
    GALLERY_WHITE: { padding: 18, background: 'linear-gradient(145deg,#ffffff,#eeece8 55%,#f7f6f3)', boxShadow: 'inset 0 0 0 1px #d8d4cd, inset 0 2px 6px rgba(0,0,0,.06), 0 22px 40px rgba(0,0,0,.22)' },
    WARM_WALNUT: {
        padding: 20,
        background: 'repeating-linear-gradient(92deg,#6b4226 0 6px,#7a4c2c 6px 11px,#5e3a21 11px 15px), linear-gradient(145deg,#7d4f2e,#4e2f1a)',
        boxShadow: 'inset 0 0 0 1px #3b2413, inset 0 2px 6px rgba(255,255,255,.15), 0 22px 40px rgba(0,0,0,.35)',
    },
};

/**
 * Shows the textured artwork on a wall, enclosed in the selected frame and optional 1" white mat.
 * Mat width is drawn proportionally to the artwork size.
 */
export default function FramedArtwork({ photoUrl, theme, intensity, frame = 'NONE', matting = false, widthIn = 12, heightIn = 16, wall = true }) {
    const style = FRAME_STYLES[frame] || FRAME_STYLES.NONE;
    const matPct = (1 / Math.max(widthIn, heightIn)) * 100;   // 1 inch relative to the longest side
    const aspect = `${widthIn} / ${heightIn}`;

    return (
        <div className={`${wall ? 'p-8 sm:p-12' : ''} rounded-2xl flex items-center justify-center`}
             style={wall ? { background: 'linear-gradient(180deg,#f4f1ec,#e7e2d9)' } : undefined}>
            <div className="w-full" style={{ maxWidth: widthIn >= heightIn ? 560 : 400, transition: 'all .3s' }}>
                <div style={{ ...style, transition: 'all .3s' }} className="rounded-sm">
                    <div style={{ padding: matting ? `${matPct}%` : 0, background: matting ? '#fbfaf7' : 'transparent', boxShadow: matting ? 'inset 0 0 0 1px #e7e3db, inset 0 3px 8px rgba(0,0,0,.08)' : 'none', transition: 'all .3s' }}>
                        <div className="relative overflow-hidden bg-white" style={{ aspectRatio: aspect }}>
                            {photoUrl ? (
                                <TextureCanvas src={photoUrl} theme={theme} intensity={intensity} className="absolute inset-0 w-full h-full object-cover"
                                               label="Your textured artwork inside the selected frame" />
                            ) : (
                                <ThemeArt theme={theme} className="absolute inset-0" />
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
