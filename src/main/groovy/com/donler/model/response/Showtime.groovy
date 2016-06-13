package com.donler.model.response

import com.donler.model.*
import groovy.transform.ToString
import io.swagger.annotations.ApiModelProperty
/**
 * Created by jason on 5/23/16.
 */
/**
 * 瞬间
 */
@ToString(includeNames = true)
class Showtime {
    @ApiModelProperty(notes = "瞬间的id")
    String id // 瞬间的id
    @ApiModelProperty(notes = "瞬间的内容")
    String content // 瞬间的内容
    @ApiModelProperty(notes = "配图的url数组")
    List<ImageUrlUnit> images // 配图的url数组
    @ApiModelProperty(notes = "属于的活动,为空则为非活动瞬间")
    String activity  // 属于的活动,为空则为非活动瞬间
    @ApiModelProperty(notes = "指定可见群组id,为空默认为全体可见")
    List<SimpleTeamModel> obviousTeams // 指定可见群组id,为空默认为全体可见
    @ApiModelProperty(notes = "发布者")
    SimpleUserModel author // 发布者
    @ApiModelProperty(notes = "点赞数组")
    List<ApproveArrItem> approves // 点赞数组
    @ApiModelProperty(notes = "评论")
    List<CommentArrItem> comments // 评论数组
    @ApiModelProperty(notes = "时间戳")
    CreateAndModifyTimestamp timestamp // 时间戳
}