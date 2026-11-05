import React, { useRef, useState } from 'react';
import axios from 'axios';
import CommissionLayout from '@/Components/Commission/CommissionLayout';
import WizardNav from '@/Components/Commission/WizardNav';
import TextureCanvas from '@/Components/Commission/TextureCanvas';
import { THEMES, useCommission, labelOf, isDigital } from '@/Components/Commission/CommissionStore';
import useStepGuard, { go } from '@/Components/Commission/useStepGuard';

const MAX_BYTES = 15 * 1024 * 1024;
const TYPES = ['image/jpeg', 'image/png'];

function UploadStep() {
    const state = useStepGuard(3);
    const { update } = useCommission();
    const inputRef = useRef(null);
    const [dragging, setDragging] = useState(false);
    const [progress, setProgress] = useState(null);
    const [error, setError] = useState(null);
    const [showOriginal, setShowOriginal] = useState(false);

    const upload = (file) => {
        setError(null);
        if (!file) return;
        if (!TYPES.includes(file.type) && !/\.(jpe?g|png)$/i.test(file.name)) { setError('Please choose a JPG or PNG image.'); return; }
        if (file.size > MAX_BYTES) { setError('That photo is larger than 15 MB. Please choose a smaller file.'); return; }
        const form = new FormData();
        form.append('photo', file);
        setProgress(0);
        axios.post('/api/commissions/upload', form, {
            headers: { 'Content-Type': 'multipart/form-data' },
            onUploadProgress: (e) => e.total && setProgress(Math.round((e.loaded / e.total) * 100)),
        }).then((r) => {
            update({ photoUrl: r.data.url, photoName: file.name });
        }).catch((e) => {
            setError((e.response && e.response.data && e.response.data.message) || 'Upload failed. Please check your connection and try again.');
        }).finally(() => setProgress(null));
    };

    const onDrop = (e) => {
        e.preventDefault();
        setDragging(false);
        upload(e.dataTransfer.files && e.dataTransfer.files[0]);
    };

    return (
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
            <div className="lg:col-span-3">
                {!state.photoUrl ? (
                    <div
                        onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
                        onDragLeave={() => setDragging(false)}
                        onDrop={onDrop}
                        className={`relative flex flex-col items-center justify-center text-center rounded-2xl border-2 border-dashed px-6 py-16 transition-colors ${
                            dragging ? 'border-brand bg-brand-soft' : 'border-gray-300 bg-white hover:border-gray-400'}`}
                    >
                        <span className="flex items-center justify-center w-16 h-16 rounded-full bg-brand-soft text-brand text-2xl"><i className="fa fa-cloud-upload" aria-hidden="true"></i></span>
                        <p className="mt-4 text-lg font-bold text-ink">Drag &amp; drop your reference photo</p>
                        <p className="mt-1 text-sm text-gray-500">JPG, JPEG or PNG · up to 15 MB · a sharp, well-lit photo works best</p>
                        <button type="button" onClick={() => inputRef.current && inputRef.current.click()}
                                className="mt-6 inline-flex items-center h-11 px-6 rounded-full bg-ink text-white font-bold hover:bg-gray-800 focus:outline-none focus:ring-2 focus:ring-offset-2">
                            Browse files
                        </button>
                        <input ref={inputRef} id="photo-input" type="file" accept=".jpg,.jpeg,.png,image/jpeg,image/png" className="sr-only"
                               aria-label="Upload reference photo" onChange={(e) => upload(e.target.files && e.target.files[0])} />
                        {progress !== null && (
                            <div className="mt-6 w-full max-w-xs" role="progressbar" aria-valuenow={progress} aria-valuemin="0" aria-valuemax="100">
                                <div className="h-2 rounded-full bg-gray-200 overflow-hidden"><div className="h-full bg-brand transition-all" style={{ width: `${progress}%` }}></div></div>
                                <p className="mt-2 text-xs text-gray-500">Uploading… {progress}%</p>
                            </div>
                        )}
                    </div>
                ) : (
                    <div className="rounded-2xl border border-gray-200 bg-white overflow-hidden shadow-sm">
                        <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-3 border-b border-gray-100">
                            <p className="text-sm font-bold text-ink"><i className="fa fa-magic text-brand mr-2" aria-hidden="true"></i>{labelOf(THEMES, state.theme)} preview</p>
                            <div className="inline-flex rounded-full bg-gray-100 p-1 text-sm" role="radiogroup" aria-label="Compare">
                                <button type="button" role="radio" aria-checked={!showOriginal} onClick={() => setShowOriginal(false)} className={`px-3 h-8 rounded-full font-bold ${!showOriginal ? 'bg-white shadow text-ink' : 'text-gray-500'}`}>Painted</button>
                                <button type="button" role="radio" aria-checked={showOriginal} onClick={() => setShowOriginal(true)} className={`px-3 h-8 rounded-full font-bold ${showOriginal ? 'bg-white shadow text-ink' : 'text-gray-500'}`}>Original</button>
                            </div>
                        </div>
                        <div className="bg-gray-50 p-4">
                            <TextureCanvas src={state.photoUrl} theme={state.theme} intensity={state.intensity} showOriginal={showOriginal} className="rounded-lg shadow" />
                        </div>
                        <div className="px-5 py-4 flex flex-col sm:flex-row sm:items-center gap-4 border-t border-gray-100">
                            <label htmlFor="intensity" className="text-sm font-bold text-ink whitespace-nowrap">Texture strength</label>
                            <input id="intensity" type="range" min="0.2" max="1" step="0.05" value={state.intensity}
                                   onChange={(e) => update({ intensity: Number(e.target.value) })} className="w-full" style={{ accentColor: "#e5156b" }} />
                            <button type="button" onClick={() => update({ photoUrl: null, photoName: null })}
                                    className="text-sm font-bold underline whitespace-nowrap text-gray-700 hover:text-ink">Replace photo</button>
                        </div>
                    </div>
                )}
                {error && <p role="alert" className="mt-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm font-semibold px-4 py-3">{error}</p>}
                <p className="mt-4 text-xs text-gray-500">This is a simulated preview to help you picture the result. Your artist paints the final piece by hand.</p>
            </div>

            <div className="lg:col-span-2">
                <label htmlFor="instructions" className="block text-xl font-extrabold tracking-tight text-ink">Special instructions</label>
                <p className="mt-1 text-sm text-gray-600">Anything your artist should know? Optional.</p>
                <textarea id="instructions" rows="8" maxLength="4000" value={state.instructions}
                          onChange={(e) => update({ instructions: e.target.value })}
                          placeholder="E.g., Please merge these two reference photos or change the background color..."
                          className="mt-3 w-full rounded-xl border-gray-300 focus:border-brand focus:ring-brand text-sm" />
                <p className="mt-1 text-right text-xs text-gray-400">{state.instructions.length}/4000</p>
                <ul className="mt-6 space-y-2 text-sm text-gray-600">
                    <li><i className="fa fa-check text-brand mr-2" aria-hidden="true"></i>Use the highest-resolution photo you have</li>
                    <li><i className="fa fa-check text-brand mr-2" aria-hidden="true"></i>Natural light and clear faces give the best likeness</li>
                    <li><i className="fa fa-check text-brand mr-2" aria-hidden="true"></i>Multiple subjects? Describe how to combine them</li>
                </ul>
            </div>
        </div>
    );
}

function Nav() {
    const { state } = useCommission();
    return (
        <WizardNav onBack={() => go('/commission/step-2')}
                   onNext={() => go(isDigital(state) ? '/commission/preview' : '/commission/step-4')}
                   nextDisabled={!state.photoUrl}
                   nextLabel={isDigital(state) ? 'Next: Review Order ➔' : 'Next: Frame Customizer ➔'}
                   hint={!state.photoUrl ? 'Upload a reference photo to continue.' : null} />
    );
}

export default function Step3Upload() {
    return (
        <CommissionLayout title="Upload your photo" subtitle="Upload a reference photo and preview it with your chosen painting texture." step={3}>
            <UploadStep />
            <Nav />
        </CommissionLayout>
    );
}
