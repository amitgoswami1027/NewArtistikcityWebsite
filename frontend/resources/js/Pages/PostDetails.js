import React, {useState, useEffect} from "react";
import { Link, Head, usePage } from '@inertiajs/inertia-react';
import Breadcrumb from '@/Components/Breadcrumb';
import Footer from '@/Components/Footer';
import Header from '@/Components/Header';
import SharePost from '@/Components/Blog/SharePost';
import PostComments from '@/Components/Blog/PostComments';
import BlogSidebar from '@/Components/Blog/BlogSidebar';
import { FacebookShareButton, TwitterShareButton, LinkedinShareButton, FacebookIcon, TwitterIcon, LinkedinIcon } from "react-share";

export default function PostDetails() {
	const { post, category_list, featured_post } = usePage().props;
	let sidebarData = {
        categoryList: category_list,
        featuredPost: featured_post
    }
	const [pageURL, setPageURL] = useState(0);
    useEffect(() => {
        setPageURL(window.location.href);
    })
	const breadcrumbs = [
        { path: 'welcome', breadcrumb: 'Home' },
        { path: 'blog.posts', breadcrumb: 'Posts'},
		{ path: 'blog.post.details', breadcrumb: post.title},
    ];
    const [crumbs, setCrumbs] = useState(breadcrumbs);
    const selected = crumb => { console.log(crumb); }
	return (
		<>
			<Header />
			<Breadcrumb crumbs={crumbs} selected={selected} />
			<div className="blogpage">
				<div className="container">
					<div className="blogleftbar">
						<div className="blogmain">
							<div className="postthumb">
								<img src={(`/storage/uploads/blog/${post.image}`)} alt={(`${post.title}`)} />
							</div>
							<h1>{post.title}</h1>
							<div className="postmeta">
								<span className="date">{post.created_at}</span>
								<span className="divider">/</span>
								<span className="postcatgry">{post.categories[0].name}</span>
								<span className="divider">/</span>
								<span className="postcomment"><a href="#comments"><i className="fa fa-comment-o"></i> Comment (2)</a></span>
							</div>
							<div className="author">
								<a href="#">
                                    <img src={(`/storage/uploads/teachers/${post.posted_by[0].id}/${post.posted_by[0].profile_photo}`)} alt={(`${post.posted_by[0].name}`)} />
                                    {post.posted_by[0].name}
                                </a>
							</div>

							<hr />

							<div className="blogcontent">
								<div dangerouslySetInnerHTML={{__html: post.body}}></div>
							</div>
							<div className="shareArticle">
								<label>Share Article</label>
								<FacebookShareButton title={post.title} url={pageURL} hashtags={["hashtag1", "hashtag2"]}>
									<FacebookIcon size={32} round /> 
								</FacebookShareButton>&nbsp;
								<TwitterShareButton title={post.title} url={pageURL} hashtags={["hashtag1", "hashtag2"]}>
									<TwitterIcon size={32} round /> 
								</TwitterShareButton>&nbsp;
								<LinkedinShareButton title={post.title} url={pageURL} hashtags={["hashtag1", "hashtag2"]}>
									<LinkedinIcon size={32} round /> 
								</LinkedinShareButton>
							</div>
							{/* <SharePost postTitle={post.title}  pageURL={pageURL}/> */}

						</div>

						<PostComments postID={post.id} />

					</div>

					<BlogSidebar sidebarData={sidebarData} />

				</div>
			</div>
			<Footer />
		</>
	);
}
