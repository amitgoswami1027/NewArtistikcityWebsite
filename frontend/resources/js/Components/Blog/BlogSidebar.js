import React from 'react';
import { Link, Head, usePage } from '@inertiajs/inertia-react';

export default function BlogSidebar({sidebarData}) {
    console.log(sidebarData);
    const categoryList = sidebarData.categoryList;
    const featuredPost = sidebarData.featuredPost;
    return (
        <>
            <div className="blogsidebar">
                <div className="widget">
                    <h3>Category</h3>
                    <div className="pd30 topcategories">
                        <ul>
                        {categoryList.map(({ id, name, slug }) => {
                            return (
                                <li><Link href={route('blog.posts', {id})}>{name}</Link></li>    
                            );
                        })}
                        </ul>
                    </div>
                </div>

                <div className="widget">
                    <h3>Featured Post</h3>
                    <div className="pd30 ftrdpost">
                        <ul>
                            {featuredPost.map(({ id, title, slug }) => {
                                return (
                                    <li><Link href={route('blog.post.details', {slug})}>{title}</Link></li>    
                                );
                            })}
                        </ul>
                    </div>
                </div>

                {/* <div className="sidebarSignup">
                    <h4>Newsletter Subscription</h4>
                    <p>Subscribe to New Blog Posts</p>
                    <div className="signupForm">
                    </div>
                </div> */}

            </div>
        </>
    );
}
