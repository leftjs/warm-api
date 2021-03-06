package com.donler.model.response

import com.donler.model.SimpleUserModel
import io.swagger.annotations.ApiModelProperty

/**
 * Created by zhangjiasheng on 7/22/16.
 */
class VoteOptionInfo {
    @ApiModelProperty("投票id")
    String id
    @ApiModelProperty("投票选项")
    String option
    @ApiModelProperty("已投用户ids")
    List<SimpleUserModel> votedUsers
    @ApiModelProperty("投该选项的人数")
    Integer count
    @ApiModelProperty("投票总人数")
    Integer totalCount
}
