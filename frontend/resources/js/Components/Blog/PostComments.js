import React from 'react';

export default function PostComments(props) {
    console.log(props);
    return (
        <>
            <div id="comments" className="blogcomments">
                <div className="commentsCount">
                    <h3><i className="fa fa-comment-o"></i> Comments (2)</h3>
                    <a href="#">Leave a Comment</a>
                </div>

                <div className="commentbox">
                    <div className="col4">
                        <img src="/assets/images/comment-user-1.jpg" alt="" />
                        <h4>Jayden E</h4>
                        <h6>3 weeks ago</h6>
                    </div>
                    <div className="col8">
                        <p>At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint qui blanditiis praesentium voluptatum deleniti atque corrupti occaecati.</p>
                    </div>
                </div>

                <div className="commentbox">
                    <div className="col4">
                        <img src="/assets/images/comment-user-2.jpg" alt="" />
                        <h4>Maria R.</h4>
                        <h6>3 weeks ago</h6>
                    </div>
                    <div className="col8">
                        <p>At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint occaecati.</p>
                    </div>
                </div>
            </div>
        </>
    );
}
