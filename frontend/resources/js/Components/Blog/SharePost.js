import React from 'react';
import { FacebookShareButton, TwitterShareButton, LinkedinShareButton, FacebookIcon, TwitterIcon, LinkedinIcon } from "react-share";

export default function SharePost(props) {
    console.log(props);
    // console.log(pageURL);
    let postTitle = '';
    let pageURL = '';
    return (
        <>
            <div className="shareArticle">
                <label>Share Article</label>
                <FacebookShareButton title={postTitle} url={pageURL} hashtags={["hashtag1", "hashtag2"]}>
                    <FacebookIcon size={32} round /> 
                </FacebookShareButton>&nbsp;
                <TwitterShareButton title={postTitle} url={pageURL} hashtags={["hashtag1", "hashtag2"]}>
                    <TwitterIcon size={32} round /> 
                </TwitterShareButton>&nbsp;
                <LinkedinShareButton title={postTitle} url={pageURL} hashtags={["hashtag1", "hashtag2"]}>
                    <LinkedinIcon size={32} round /> 
                </LinkedinShareButton>
            </div>
        </>
    );
}
