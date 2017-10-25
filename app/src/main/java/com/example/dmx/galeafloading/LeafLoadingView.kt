package com.example.dmx.galeafloading

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.util.*

/**
 * Created by dmx on 17-10-24.
 */
open class LeafLoadingView : View {
    companion object {
        private val TAG = "LeafLoadingView"
        //浅白色
        private val WHITE_COLOR = Color.parseColor("#fde399")
        //橙色
        private val ORANGE_COLOR = Color.parseColor("#ffa800")
        //中等振幅大小
        private val MIDDLE_AMPLITUDE = 13
        //不同类型之间的振幅差距
        private val AMPLITUDE_DISPARITY = 5

        //总进度
        private val TOTAL_PROGRESS = 100
        //叶子飘动一个周期所花的时间
        private val LEAF_FLOAT_TIME = 3000
        //叶子旋转一周需要的时间
        private val LEAF_ROTATE_TIME = 2000

        //用于控制绘制的进度条距离左/上/下的距离
        private val LEFT_MARGIN = 9
        //用于控制绘制的进度条距离右的距离
        private val RIGHT_MARGIN = 25

    }

    private var mLeftMargin = 0
    private var mRightMargin = 0
    //中等振幅大小
    private var mMiddleAmplitude = MIDDLE_AMPLITUDE
    //振幅差
    private var mAmplitudeDisparity = AMPLITUDE_DISPARITY

    //叶子飘动一个周期所花的时间
    private var mLeafFloatTime: Int = LEAF_FLOAT_TIME
    //叶子旋转一周所需要的时间
    private var mLeafRotateTime: Int = LEAF_ROTATE_TIME

    private var mResources: Resources
    private lateinit var mLeafBitmap: Bitmap
    private var mLeafWidth = 0
    private var mLeafHeight = 0

    private lateinit var mOuterBitmap: Bitmap
    private lateinit var mOuterSrcRect: Rect
    private lateinit var mOuterDestRect: Rect
    private var mOuterWidth = 0
    private var mOuterHeight = 0
    private var mTotalWidth = 0
    private var mTotalHeight = 0

    private lateinit var mBitmapPaint: Paint
    private lateinit var mWhitePaint: Paint
    private lateinit var mOrangePaint: Paint
    private lateinit var mWhiteRectF: RectF
    private lateinit var mOrangeRectF: RectF
    private lateinit var mArcRectF: RectF


    private var mProgress = 0
    private var mProgressWidth = 0
    private var mCurrentProgressPosition = 0
    private var mArcRadius = 0

    private var mArcRightLocation = 0

    private var mLeafFactory: LeafFactory
    private var mLeafInfos: List<Leaf>
    private var mAddTime = 0


    constructor(context: Context) : this(context, null)
    constructor(context: Context, attributeSet: AttributeSet?) : this(context, attributeSet, 0)
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int) : super(context, attributeSet, defStyleAttr) {
        mResources = resources
        mLeftMargin = UiUtils.dipToPx(context, LEFT_MARGIN)
        mRightMargin = UiUtils.dipToPx(context, RIGHT_MARGIN)

        mLeafFloatTime = LEAF_FLOAT_TIME
        mLeafRotateTime = LEAF_ROTATE_TIME

        initBitmap()
        initPaint()
        mLeafFactory = LeafFactory()
        mLeafInfos = mLeafFactory.generateLeafs()
    }


    private fun initPaint() {
        mBitmapPaint = Paint()
        mBitmapPaint.isAntiAlias = true
        mBitmapPaint.isDither = true
        mBitmapPaint.isFilterBitmap = true

        mWhitePaint = Paint()
        mWhitePaint.isAntiAlias = true
        mWhitePaint.color = WHITE_COLOR

        mOrangePaint = Paint()
        mOrangePaint.isAntiAlias = true
        mOrangePaint.color = ORANGE_COLOR
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawProgressAndLeafs(canvas)
        //绘制外框图片
        canvas.drawBitmap(mOuterBitmap, mOuterSrcRect, mOuterDestRect, mBitmapPaint)
        postInvalidate()
    }

    /**
     * 绘制进度和叶子
     */
    private fun drawProgressAndLeafs(canvas: Canvas) {
        if (mProgress >= TOTAL_PROGRESS) {
            mProgress = 0
        }
        mCurrentProgressPosition = mProgressWidth * mProgress / TOTAL_PROGRESS
        //如果当前进度对应的长度小于半径
        if (mCurrentProgressPosition < mArcRadius) {
            //首先绘制一个白色的半圆
            canvas.drawArc(mArcRectF, 90F, 180F, false, mWhitePaint)
            //绘制白色矩形
            mWhiteRectF.left = mArcRightLocation.toFloat()
            canvas.drawRect(mWhiteRectF, mWhitePaint)
            //绘制叶子
            drawLeafs(canvas)
            //绘制橙色圆弧
            val angle = Math.toDegrees(Math.acos((mArcRadius - mCurrentProgressPosition).toDouble() / mArcRadius))
            val startAngle = 180 - angle
            val sweepAngle = 2 * angle
            canvas.drawArc(mArcRectF, startAngle.toFloat(), sweepAngle.toFloat(), false, mOrangePaint)
        } else {
            //绘制白色矩形部分
            mWhiteRectF.left = mCurrentProgressPosition.toFloat()
            canvas.drawRect(mWhiteRectF, mWhitePaint)
            //绘制叶子
            drawLeafs(canvas)
            //绘制橙色半圆
            canvas.drawArc(mArcRectF, 90F, 180F, false, mOrangePaint)
            //绘制橙色矩形部分
            mOrangeRectF.left = mArcRightLocation.toFloat()
            mOrangeRectF.right = mCurrentProgressPosition.toFloat()
            canvas.drawRect(mOrangeRectF, mOrangePaint)
        }
    }

    /**
     * 绘制叶子
     */
    private fun drawLeafs(canvas: Canvas) {
        mLeafRotateTime = if (mLeafRotateTime <= 0) LEAF_ROTATE_TIME else mLeafRotateTime
        val currentTime = System.currentTimeMillis()
        for (leaf in mLeafInfos) {
            //当前时间大于叶子的开始时间则进行绘制
            if (currentTime > leaf.startTime && leaf.startTime != 0L) {
                //根据时间计算叶子的位置
                getLeafLocation(leaf, currentTime)
                canvas.save()
                val matrix = Matrix()
                val transX = mLeftMargin + leaf.x
                val transY = mLeftMargin + leaf.y
                //设置叶子的偏移距离
                matrix.postTranslate(transX, transY)
                //根据当前时间、叶子的开始时间和叶子的自转周期计算旋转角度
                val rotateFraction = ((currentTime - leaf.startTime) % mLeafRotateTime).toFloat() / mLeafRotateTime
                val angle = rotateFraction * 360
                val rotate = if (leaf.rotateDirection == 0) angle + leaf.rotateAngle else -angle + leaf.rotateAngle
                matrix.postRotate(rotate, transX + mLeafWidth / 2, transY + mLeafHeight / 2)
                canvas.drawBitmap(mLeafBitmap, matrix, mBitmapPaint)
                canvas.restore()
            } else {
                continue
            }
        }
    }

    /**
     * 根据当前时间计算叶子的位置
     */
    private fun getLeafLocation(leaf: Leaf, currentTime: Long) {
        var intervalTime = currentTime - leaf.startTime
        mLeafFloatTime = if (mLeafFloatTime <= 0) LEAF_FLOAT_TIME else mLeafFloatTime
        if (intervalTime < 0) {
            return
        } else if (intervalTime > mLeafFloatTime) {
            leaf.startTime = System.currentTimeMillis() + Random().nextInt(mLeafFloatTime)
        }
        //根据当前时间和设定的叶子飞行时间计算叶子应该飞到什么位置
        val fraction = intervalTime.toFloat() / mLeafFloatTime
        leaf.x = mProgressWidth - mProgressWidth * fraction
        leaf.y = getLeafLocationY(leaf)
    }

    /**
     * 根据正弦曲线公式计算Y轴位置
     */
    private fun getLeafLocationY(leaf: Leaf): Float {
        val w = 2 * Math.PI / mProgressWidth
        val a = when (leaf.type) {
            StartType.LITTLE -> mMiddleAmplitude - mAmplitudeDisparity
            StartType.MIDDLE -> mMiddleAmplitude
            StartType.BIG -> mMiddleAmplitude + mAmplitudeDisparity
            else -> mMiddleAmplitude
        }
        return ((a * Math.sin(w * leaf.x) + mArcRadius * 2 / 3).toFloat())
    }

    private fun initBitmap() {
        mLeafBitmap = (mResources.getDrawable(R.drawable.leaf) as BitmapDrawable).bitmap
        mLeafWidth = mLeafBitmap.width
        mLeafHeight = mLeafBitmap.height

        mOuterBitmap = (mResources.getDrawable(R.drawable.leaf_kuang) as BitmapDrawable).bitmap
        mOuterWidth = mOuterBitmap.width
        mOuterHeight = mOuterBitmap.height
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mTotalWidth = w
        mTotalHeight = h
        mProgressWidth = mTotalWidth - mLeftMargin - mRightMargin
        mArcRadius = (mTotalHeight - 2 * mLeftMargin) / 2
        mOuterSrcRect = Rect(0, 0, mOuterWidth, mOuterHeight)
        mOuterDestRect = Rect(0, 0, mTotalWidth, mTotalHeight)

        mWhiteRectF = RectF((mLeftMargin + mCurrentProgressPosition).toFloat(), mLeftMargin.toFloat(), (mTotalWidth - mRightMargin).toFloat(), (mTotalHeight - mLeftMargin).toFloat())
        mOrangeRectF = RectF((mLeftMargin + mArcRadius).toFloat(), mLeftMargin.toFloat(), mCurrentProgressPosition.toFloat(), (mTotalHeight - mLeftMargin).toFloat())

        mArcRectF = RectF(mLeftMargin.toFloat(), mLeftMargin.toFloat(), (mLeftMargin + 2 * mArcRadius).toFloat(), (mTotalHeight - mLeftMargin).toFloat())

        mArcRightLocation = mLeftMargin + mArcRadius
    }

    /**
     * 设置中等振幅
     *
     * @param amplitude
     */
    fun setMiddleAmplitude(amplitude: Int) {
        this.mMiddleAmplitude = amplitude
    }

    /**
     * 设置振幅差
     *
     * @param disparity
     */
    fun setMplitudeDisparity(disparity: Int) {
        this.mAmplitudeDisparity = disparity
    }

    /**
     * 获取中等振幅
     *
     * @param amplitude
     */
    fun getMiddleAmplitude(): Int {
        return mMiddleAmplitude
    }

    /**
     * 获取振幅差
     *
     * @param disparity
     */
    fun getMplitudeDisparity(): Int {
        return mAmplitudeDisparity
    }

    /**
     * 设置进度
     *
     * @param progress
     */
    fun setProgress(progress: Int) {
        this.mProgress = progress
        postInvalidate()
    }

    /**
     * 设置叶子飘完一个周期所花的时间
     *
     * @param time
     */
    fun setLeafFloatTime(time: Int) {
        this.mLeafFloatTime = time
    }

    /**
     * 设置叶子旋转一周所花的时间
     *
     * @param time
     */
    fun setLeafRotateTime(time: Int) {
        this.mLeafRotateTime = time
    }

    /**
     * 获取叶子飘完一个周期所花的时间
     */
    fun getLeafFloatTime(): Int {
        mLeafFloatTime = if (mLeafFloatTime === 0) LEAF_FLOAT_TIME else mLeafFloatTime
        return mLeafFloatTime
    }

    /**
     * 获取叶子旋转一周所花的时间
     */
    fun getLeafRotateTime(): Int {
        mLeafRotateTime = if (mLeafRotateTime === 0) LEAF_ROTATE_TIME else mLeafRotateTime
        return mLeafRotateTime
    }

    private inner class LeafFactory {
        val MAX_LEAFS = 8
        val random = Random()
        fun generateLeaf(): Leaf {
            val leaf = Leaf()
            val randomType = random.nextInt(3)
            val type = when (randomType) {
                0 -> StartType.MIDDLE
                1 -> StartType.LITTLE
                2 -> StartType.BIG
                else -> StartType.MIDDLE
            }
            leaf.type = type
            leaf.rotateAngle = random.nextInt(360)
            leaf.rotateDirection = random.nextInt(2)
            mLeafFloatTime = if (mLeafFloatTime <= 0) LEAF_FLOAT_TIME else mLeafFloatTime
            mAddTime += random.nextInt(mLeafFloatTime * 2)
            leaf.startTime = System.currentTimeMillis() + mAddTime
            return leaf
        }

        fun generateLeafs(leafSize: Int): List<Leaf> {
            val leafs = LinkedList<Leaf>()
            for (i in 0 until leafSize) {
                leafs.add(generateLeaf())
            }
            return leafs
        }

        fun generateLeafs(): List<Leaf> {
            return generateLeafs(MAX_LEAFS)
        }
    }

    private enum class StartType {
        LITTLE, MIDDLE, BIG
    }

    private class Leaf {
        var x: Float = 0F
        var y: Float = 0F
        var type: StartType? = null
        var rotateAngle = 0
        var rotateDirection = 0
        var startTime = 0L
    }
}