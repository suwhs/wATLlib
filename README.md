wATL for Android 
==========

License
=====
 Community version under GPL v3 and Commercial License

 [Need Apache 2.0 License! - support project on indiegogo](https://www.indiegogo.com/projects/make-mobile-applications-text-looks-more-accuracy-android-ios/x/17073547) - or just send some ETH to 0x1D50523b85A2A5283027b8ccB109a4bDc9c79EB1 - any help is appreciated
 
Description
======
*wATL* is a library for android applications with features:
  - formatted text full justification,
  - wrap text around images, 
  - auto-hyphenation,
  - adapter for show paginated article with stock ViewPager
  - supported Android version - 2.3 - 7.1+ (both runtimes - ART and DALVIK)
  - supports animation with DrawableSpan
  - and more...

Demo Application available on Google Play

<a href="https://play.google.com/store/apps/details?id=su.whs.watl.samples">
<img src="https://developer.android.com/images/brand/en_generic_rgb_wo_45.png" alt="Get it on Google Play" />
</a>

[![Demo Video on Youtube](http://img.youtube.com/vi/ZtXvyS6GHGo/0.jpg)](https://youtu.be/ZtXvyS6GHGo)


Quick Start
======
Usage:

wATLlib published on jcenter repository, so just

add to dependencies :
```gradle
compile 'su.whs:wATLlib:1.4.6a'
```

more info <a href="http://whs.su/">whs.su</a> 


<a href="http://whs.su">commercial license for non-gpl project on whs.su</a>


Published Classes
========

some description on <a href="https://github.com/suwhs/wATL/wiki">Wiki</a>

- *su.whs.watl.ui.TextViewWS* - base class with methods for handling text selection
    <a href="https://github.com/suwhs/wATL/blob/master/screenshots/TextViewWS1.png">screenshot 1</a>
- *su.whs.watl.ui.ClickableSpanListener* - interface for easy handle clicks on drawable 
onClick() method receive view, span position, and coordinates of image within view

- *su.whs.watl.ui.TextViewEx* - class (replacement for stock TextView) with full text justification support (enabled by default)
    <a href="https://github.com/suwhs/wATL/blob/master/screenshots/TextViewExScrollView1.png">screenshot 2</a>

- *su.whs.watl.text.BaseTextPagerAdapter* - class for using with ViewPager


Contacts
========
<a href="mailto:info@whs.su">info@whs.su</a>



