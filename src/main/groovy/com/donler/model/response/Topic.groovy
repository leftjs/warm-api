package com.donler.model.response

import com.donler.model.Constants
import com.donler.model.SimpleCompanyModel
import com.donler.model.SimpleTeamModel
import com.donler.model.SimpleUserModel
import groovy.transform.ToString
import io.swagger.annotations.ApiModelProperty
/**
 * Created by jason on 7/27/16.
 */
@ToString(includeNames = true)
class Topic {
    String id // 编号
    @ApiModelProperty("话题的配图url,可以为空")
    String image
    @ApiModelProperty("话题的所属的公司")
    SimpleCompanyModel company
    @ApiModelProperty("指定话题所在群组的id,为空默认为全体可见")
    SimpleTeamModel team // 指定群组id,为空默认为全体可见
    @ApiModelProperty("话题内容")
    String content // 投票内容
    @ApiModelProperty("话题标题")
    String title
    @ApiModelProperty("投票的评论信息")
    List<CommentArrItem> comments
    @ApiModelProperty("投票发起人")
    SimpleUserModel author
    @ApiModelProperty("创建时间")
    Date createdAt // 创建时间
    @ApiModelProperty("更新时间")
    Date updatedAt // 更新时间
    @ApiModelProperty(notes = "动态类型")
    Constants.TypeEnum typeEnum //动态类型
}
