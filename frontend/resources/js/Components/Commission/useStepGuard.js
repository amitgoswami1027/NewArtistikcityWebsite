import { useEffect } from 'react';
import { Inertia } from '@inertiajs/inertia';
import { useCommission } from '@/Components/Commission/CommissionStore';

export const go = (url, replace = false) => Inertia.visit(url, { replace, preserveScroll: false });

/** Sends the visitor back to the first incomplete step if they open a later step directly. */
export default function useStepGuard(step) {
    const { state } = useCommission();
    useEffect(() => {
        if (step > 1 && !state.theme) return go('/commission/step-1', true);
        if (step > 2 && !state.fulfillmentType) return go('/commission/step-2', true);
        if (step > 3 && !state.photoUrl) return go('/commission/step-3', true);
        return undefined;
    }, [step]);
    return state;
}
