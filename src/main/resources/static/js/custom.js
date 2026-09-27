jQuery('.testSlider').owlCarousel({
    loop:true,
    margin:0,
    responsiveClass:true,
	nav:true,
	autoplay:false,
	center: true,
	autoplayTimeout:2500,
	smartSpeed:800,
	autoplayHoverPause:true,
	animateIn: 'fadeIn',
    animateOut: 'fadeOut',
    responsive:{
        0: {
            items: 1,
        },
		770: {
            items:2,
        },
       
        980: {
            items:3,
        }
    }
});

jQuery('.reviewSlider').owlCarousel({
    loop:true,
    margin:0,
	autoplay:true,
	nav:true,
    autoplayTimeout:5000,
    autoplayHoverPause:true,
	autoplaySpeed:2000,
	autoplay:false,
    autoplayHoverPause:true,
	smartSpeed:1000,
	responsive:{
        0:{
            items:1,
        }
    }
});

jQuery('.courseslider').owlCarousel({
    loop:true,
    margin:0,
    responsiveClass:true,
	nav:true,
	autoplayTimeout:2500,
	smartSpeed:800,
	autoplayHoverPause:true,
    responsive:{
        0: {
            items: 1,
        },
		770: {
            items:2,
        },
       
        980: {
            items:3,
        }
    }
});

jQuery('.mBtn').click(function(){
  jQuery(this).toggleClass('active');
  jQuery('.navigation').slideToggle();
});






jQuery('.navigation .has-child').find('ul').before('<span class="trigger"></span>');
 jQuery('.navigation .trigger').click(function(){
 jQuery(this).toggleClass('active').next('ul').slideToggle();
});


jQuery('.coursedropdown').click(function(){
  jQuery(this).toggleClass('active');
  jQuery('.menudropdown').slideToggle();
});





// Change tab class and display content
jQuery('.tabs a').on('click', function (event) {
  event.preventDefault();  
  jQuery('.tab-active').removeClass('tab-active');
  jQuery(this).parent().addClass('tab-active');
  jQuery('.stcontent .tabcontent').hide();
  jQuery(jQuery(this).attr('href')).show();
});
jQuery('.tabs a:first').trigger('click');


jQuery(document).ready(function() { 
  jQuery(".faqList h3").eq(0).addClass("active"); 
  jQuery(".faqList div").eq(0).show(); 
  jQuery(".faqList h3").click(function() { 
  jQuery(this).next("div").slideToggle("mediam") .siblings("div:visible").slideUp("mediam"); 
  jQuery(this).toggleClass("active"); jQuery(this).siblings("h3").removeClass("active");
  }); 
});
