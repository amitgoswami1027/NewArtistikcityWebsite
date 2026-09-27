import React, { useState } from 'react';
import { usePage } from '@inertiajs/inertia-react';

const Tabs = ({ tabsData }) => {
    const [currentTab, setCurrentTab] = useState('1');
    console.log(tabsData);
    const tabs = tabsData;
    // const tabs = [
    //     {
    //         id: 1,
    //         tabTitle: 'Introduction',
    //         title: 'Introduction',
    //         content: 'Las tabs se generan automáticamente a partir de un array de objetos, el cual tiene las propiedades: id, tabTitle, title y content.'
    //     },
    //     {
    //         id: 2,
    //         tabTitle: 'Course Summary',
    //         title: 'Course Summary',
    //         content: 'Contenido de tab 2.'
    //     },
    //     {
    //         id: 3,
    //         tabTitle: 'Course Schedule',
    //         title: 'Course Schedule',
    //         content: 'Contenido de tab 3.'
    //     }
    // ];

    const handleTabClick = (e) => {
        setCurrentTab(e.target.id);
    }

    return (
        <div className='tabswrap'>
            <ul className="tabs">
                {tabs.map((tab, i) =>
                    <li key={i} className={`${currentTab == tab.id && 'tab-active'}`}><a key={i} id={tab.id} disabled={currentTab === `${tab.id}`} onClick={(handleTabClick)}>{tab.tabTitle}</a></li>
                )}
            </ul>
            <div className='stcontent'>
                {tabs.map((tab, i) =>
                    <div key={i} className="tabcontent">
                        {currentTab === `${tab.id}` && <div className="introtext"><p className='title'>{tab.title}</p><p>{tab.content}</p></div>}
                    </div>
                )}
            </div>
        </div>
    );
}

export default Tabs;