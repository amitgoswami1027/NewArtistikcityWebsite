import React from 'react';
import ReactDOM from "react-dom";
import {Link} from "@inertiajs/inertia-react";

const breadcrumb = {
    // backgroundColor: 'white',
    // border: '1px solid rgba(0, 0, 0, 0.125)',
    // borderRadius: '0.37rem'
}

function Breadcrumb(props){
    function isLast(index) {
        return index === props.crumbs.length - 1;
    }
    return (
        <>
            {/* <div className="breadCrumb">
                <div className="container">
                <ul>
                        <li><Link href="index.html">Home</Link></li>
                        <li>Login</li>
                    </ul>
                </div>`
            </div> */}

            <div className="breadCrumb">
                <div className="container">
                    <ul style={ breadcrumb }>
                    {
                        props.crumbs.map((crumb, ci) => {
                            const disabled = isLast(ci) ? 'disabled' : '';
                
                            return (
                                <li key={ ci }>
                                    {disabled != "disabled" ? (
                                        <a href={route(`${crumb.path}`)} className={ `${ disabled }` } onClick={ () => props.selected(crumb.path) }>{ crumb.breadcrumb }</a>
                                    ):(
                                        <span>{ crumb.breadcrumb }</span>
                                    )}
                                    
                                </li>
                            );
                        })
                    }
                    </ul>
                </div>
            </div>
        </>
    );
}

export default Breadcrumb;

if (document.getElementById('breadcrumb')) {
    ReactDOM.render(<Breadcrumb />, document.getElementById('breadcrumb'));
}
