$(document).ready(function(){
	var Get_Column_Height = $( '.item .vdeoCol' ).innerWidth();
	$( '.item .vdeoCol' ).css( 'height', Get_Column_Height + 20 );
	$( window ).resize( function( e ) {
		var Get_Column_Height = $( '.item .vdeoCol' ).innerWidth();
		$( '.item .vdeoCol' ).css( 'height', Get_Column_Height + 20 );
	});
});

$(document).ready(function(){
  var Get_Column_Height = $( '.videosRow .vdeoCol' ).innerWidth();
  $( '.videosRow .vdeoCol' ).css( 'height', Get_Column_Height + 140 );
  $( window ).resize( function( e ) {
	  var Get_Column_Height = $( '.videosRow .vdeoCol' ).innerWidth();
	  $( '.videosRow .vdeoCol' ).css( 'height', Get_Column_Height + 140 );
  });
  if ($(window).width() < 1300) {
    $( '.videosRow .vdeoCol' ).css( 'height', Get_Column_Height + 120 );
    $( window ).resize( function( e ) {
	  var Get_Column_Height = $( '.videosRow .vdeoCol' ).innerWidth();
	  $( '.videosRow .vdeoCol' ).css( 'height', Get_Column_Height + 120 );
  });
  }
  if ($(window).width() < 767) {
    $( '.videosRow .vdeoCol' ).css( 'height', Get_Column_Height + 60 );
    $( window ).resize( function( e ) {
	  var Get_Column_Height = $( '.videosRow .vdeoCol' ).innerWidth();
	  $( '.videosRow .vdeoCol' ).css( 'height', Get_Column_Height + 60 );
  });
  }
});



$('.loginUser a').click(function(){
  $('#togglProfile').addClass('active');
   $('#togglMenu').removeClass('active');
   $('#addEvents').removeClass('active');
});


$('.menubtn').click(function(){
  $('#togglMenu').addClass('active');
  $('#addEvents').removeClass('active');
  $('#togglProfile').removeClass('active');
});


$('.toggleEvent').click(function(){
 $(this).toggleClass('active');
  $('#addEvents').addClass('active');
  $('#togglMenu').removeClass('active');
  $('#togglProfile').removeClass('active');
});

$('.toggleToast1').click(function(){
 $(this).toggleClass('active');
  $('#toastMsg1').slideToggle();
  $('#togglMenu').removeClass('active');
  $('#togglProfile').removeClass('active');
});


$('.toggleToolTip').click(function(){
 $(this).toggleClass('active');
  $('.mobileTooltip').slideToggle();
});

$('.close').click(function(){
  $('#togglMenu').removeClass('active');
  $('#addEvents').removeClass('active');
  $('#togglProfile').removeClass('active');
  $('.toggleEvent').removeClass('active');
  $('.toggleToolTip').removeClass('active');

});

$('.vwCmnts').click(function(){
 $(this).toggleClass('active');
  $('.commentlist').slideToggle();
});

$('.comntReply').click(function(){
 $(this).toggleClass('active');
});


$('.fullTitle').click(function(){
  $(this).hide('');
  $(this).prev().addClass('full');
});


$('.vmeta button').click(function(){
  $(this).toggleClass('active');
});

$('.togleActn1').click(function(){
  $(this).next('.d2popbox').slideToggle();
});

$('.showshare').click(function(){
  $('.shareList').toggleClass('active');
});
$('.overlay').click(function(){
  $('.shareList').toggleClass('active');
});



if ($.fn.slick) $('.creationSlider').slick({
  infinite: true,
  slidesToShow: 4,
  slidesToScroll: 1,
  arrows: true,
  autoplay: true,
  responsive: [
    {
      breakpoint: 1024,
      settings: {
        slidesToShow: 3,
      }
    },
    {
      breakpoint: 767,
      settings: {
        slidesToShow: 2,
      }
    },
	{
      breakpoint: 540,
      settings: {
        slidesToShow: 2,
      }
    }
  ]
});

$(document).ready(function() {
    if ($.fn.magnificPopup) $('.popup').magnificPopup({
        type: 'inline',
        midClick: true
    });
});


$(document).ready(function() {
   if ($.fn.tooltipster) $('.tooltip').tooltipster();
});