function scrollToBottom() {
    window.scrollTo(0,document.body.scrollHeight);
}

function scrollToElement(id) {
    var elem = document.getElementById(id);
    var x = 0;
    var y = 0;

    while (elem != null) {
        x += elem.offsetLeft;
        y += elem.offsetTop;
        elem = elem.offsetParent;
    }

    window.scrollTo(x, y);
}

function toggleSpoiler(obj){
	var div = obj.getElementsByTagName('div');
	if (div[0]) {
		if (div[0].style.visibility == "visible") {
			div[0].style.visibility = 'hidden';
		}
		else if (div[0].style.visibility == "hidden" || !div[0].style.visibility) {
			div[0].style.visibility = 'visible';
		}
	}
}

/**
 *
 * Kindly provided by : http://codepen.io/fronterweb/pen/jcwgx
 *
 * Helper function, that allows to attach multiple events to selected objects
 * @param {[object]}   el       [selected element or elements]
 * @param {[type]}   events   [DOM object events like click or touch]
 * @param {Function} callback [Callback method]
 */
var addMulitListener = function(el, events, callback) {
  // Split all events to array
  var e = events.split(' ');

    // Loop trough all elements
    Array.prototype.forEach.call(el, function(element, i) {
      // Loop trought all events and add event listeners to each
      Array.prototype.forEach.call(e, function(event, i) {
        element.addEventListener(event, callback, false);
      });
    });
};

document.addEventListener("DOMContentLoaded", function(event) {
    /**
     * This function is adding ripple effect to elements
     * @param  {[object]} e [DOM objects, that should apply ripple effect]
     * @return {[null]}   [description]
     */
    addMulitListener(document.querySelectorAll('[material]'), 'click touchstart', function(e) {
        var ripple = this.querySelector('.ripple');
        var eventType = e.type;
        /**
         * Ripple
         */
        if(ripple == null) {
          // Create ripple
          ripple = document.createElement('span');
          ripple.classList.add('ripple');

          // Prepend ripple to element
          this.insertBefore(ripple, this.firstChild);

          // Set ripple size
          if(!ripple.offsetHeight && !ripple.offsetWidth) {
            var size = Math.max(e.target.offsetWidth, e.target.offsetHeight);
            ripple.style.width = size + 'px';
            ripple.style.height = size + 'px';
          }

        }

        // Remove animation effect
        ripple.classList.remove('animate');

        // get click coordinates by event type
        if(eventType == 'click') {
          var x = e.pageX;
          var y = e.pageY;
        } else if(eventType == 'touchstart') {
          var x = e.changedTouches[0].pageX;
          var y = e.changedTouches[0].pageY;
        }
        x = x - this.offsetLeft - ripple.offsetWidth / 2;
        y = y - this.offsetTop - ripple.offsetHeight / 2;

        // set new ripple position by click or touch position
        ripple.style.top = y + 'px';
        ripple.style.left = x + 'px';
        ripple.classList.add('animate');
    });
});
