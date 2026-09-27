import React from 'react';
import { Link } from '@inertiajs/inertia-react';

export default function Pagination({links}) {
    // console.log(links);
    return (
        <nav aria-label="Page navigation example">
             <ul class="pagination">
                {links.map((link, i) => {
                    return (
                        <li key={i} className={`${link.active == true && 'current'} page-item`}><Link className='page-link' href={`${link.url != null && link.url}`}><span dangerouslySetInnerHTML={{__html: link.label}}></span></Link></li>
                    )
                })}
            </ul>
        </nav>
    );
}