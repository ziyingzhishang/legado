package io.legado.app.ui.widget.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import io.legado.app.help.ReadBookConfig
import io.legado.app.ui.widget.page.delegate.PageDelegate
import io.legado.app.ui.widget.page.delegate.SlidePageDelegate

class PageView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs), PageDelegate.PageInterface {

    var callback: CallBack? = null
    private var pageDelegate: PageDelegate? = null
    private var pageFactory: TextPageFactory? = null

    var prevPage: ContentView? = null
    var curPage: ContentView? = null
    var nextPage: ContentView? = null

    init {
        prevPage = ContentView(context)
        addView(prevPage)
        nextPage = ContentView(context)
        addView(nextPage)
        curPage = ContentView(context)
        addView(curPage)
        upBg()
        setWillNotDraw(false)

        pageDelegate = SlidePageDelegate(this)

        setPageFactory(TextPageFactory.create(object : DataSource {
            override fun isPrepared(): Boolean {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun getChapterPosition(): Int {
                return callback?.durChapterIndex() ?: 0
            }

            override fun getChapter(position: Int): TextChapter? {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun getCurrentChapter(): TextChapter? {
                return callback?.textChapter(0)
            }

            override fun getNextChapter(): TextChapter? {
                return callback?.textChapter(1)
            }

            override fun getPreviousChapter(): TextChapter? {
                return callback?.textChapter(-1)
            }

            override fun hasNextChapter(): Boolean {
                callback?.let {
                    return it.durChapterIndex() < it.chapterSize() - 1
                }
                return false
            }

            override fun hasPrevChapter(): Boolean {
                callback?.let {
                    return it.durChapterIndex() > 0
                }
                return false
            }
        }))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        pageDelegate?.setViewSize(w, h)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        pageDelegate?.onDraw(canvas)
    }

    override fun computeScroll() {
        pageDelegate?.scroll()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return pageDelegate?.onTouch(event) ?: super.onTouchEvent(event)
    }

    fun chapterLoadFinish(chapterOnDur: Int = 0) {
        callback?.let { cb ->
            when (chapterOnDur) {
                0 -> {
                    cb.textChapter()?.let {
                        curPage?.setContent(it.page(cb.durChapterPos(it.pageSize())))
                        if (cb.durChapterPos(it.pageSize()) > 0) {
                            prevPage?.setContent(it.page(cb.durChapterPos(it.pageSize()) - 1))
                        }
                        if (cb.durChapterPos(it.pageSize()) < it.pageSize() - 1) {
                            nextPage?.setContent(it.page(cb.durChapterPos(it.pageSize()) + 1))
                        }
                    }
                }
                1 -> {
                    cb.textChapter()?.let {
                        if (cb.durChapterPos(it.pageSize()) == it.pageSize() - 1) {
                            nextPage?.setContent(cb.textChapter(1)?.page(0))
                        }
                    }
                }
                -1 -> {
                    cb.textChapter()?.let {
                        if (cb.durChapterPos(it.pageSize()) == 0) {
                            prevPage?.setContent(cb.textChapter(-1)?.lastPage())
                        }
                    }
                }
                else -> {
                }
            }
        }
    }

    fun fillPage(direction: PageDelegate.Direction) {
        pageFactory?.let {
            when (direction) {
                PageDelegate.Direction.PREV -> {
                    it.moveToPrevious()
                }

                PageDelegate.Direction.NEXT -> {
                    it.moveToNext()
                }
                else -> {

                }
            }

            prevPage?.setContent(it.previousPage())
            curPage?.setContent(it.currentPage())
            nextPage?.setContent(it.nextPage())
        }
    }

    fun setPageFactory(factory: TextPageFactory) {
        this.pageFactory = factory

        //可做成异步回调
        pageFactory?.let {
            prevPage?.setContent(it.previousPage())
            curPage?.setContent(it.currentPage())
            nextPage?.setContent(it.nextPage())
        }
    }

    override fun hasNext(): Boolean {
        return true
    }

    override fun hasPrev(): Boolean {
        return true
    }

    fun upStyle() {
        curPage?.upStyle()
        prevPage?.upStyle()
        nextPage?.upStyle()
    }

    fun upBg() {
        ReadBookConfig.bg ?: let {
            ReadBookConfig.upBg()
        }
        curPage?.setBg(ReadBookConfig.bg)
        prevPage?.setBg(ReadBookConfig.bg)
        nextPage?.setBg(ReadBookConfig.bg)
    }

    fun upTime() {
        curPage?.upTime()
        prevPage?.upTime()
        nextPage?.upTime()
    }

    fun upBattery(battery: Int) {
        curPage?.upBattery(battery)
        prevPage?.upBattery(battery)
        nextPage?.upBattery(battery)
    }

    interface CallBack {
        fun chapterSize(): Int
        fun durChapterIndex(): Int
        fun durChapterPos(pageSize: Int): Int
        fun textChapter(chapterOnDur: Int = 0): TextChapter?
        fun loadChapter(index: Int)
    }
}
