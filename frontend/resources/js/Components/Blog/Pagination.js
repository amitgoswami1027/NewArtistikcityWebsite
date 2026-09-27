import React from 'react';
import { Link } from '@inertiajs/inertia-react';

export default function Pagination({links}) {
    // console.log(links);
    return (
        <div className="pagination">
            <ul>
                {links.map((link, i) => {
                    return (
                        <li key={i} className={`${link.active == true && 'current'}`}><Link href={`${link.url != null && link.url}`}><span dangerouslySetInnerHTML={{__html: link.label}}></span></Link></li>
                    )
                })}
            </ul>
        </div>
    );
}